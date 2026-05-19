package com.example.idledungeon.engine

import com.example.idledungeon.model.*
import com.example.idledungeon.notification.ExpeditionEvent
import com.example.idledungeon.notification.ExpeditionEventBus
import com.example.idledungeon.viewmodel.GameUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ExplorationEngine 测试 — 协程驱动 + StateFlow 状态机, 三个引擎里最复杂的一个.
 *
 * 测试策略:
 *   - 用 runTest 让 BASE_INTERVAL_MS=600 的 delay() 全部走虚拟时间 (毫秒级完成)
 *   - 手工构造小地图 (5-10 格走廊 或 死胡同) 控制流程, 不让 DungeonGenerator 介入
 *   - 把地图预设 explored=true, 让 BFS 立即可用, 不需要等视野揭示
 *   - 副作用通过 stateFlow.value 检查; 事件通过 ExpeditionEventBus 订阅检查
 *
 * 测试 EventBus 的关键点:
 *   - ExpeditionEventBus 是全局单例 SharedFlow (replay=0)
 *   - 用 UnconfinedTestDispatcher 让 launch 立即开始 collect, 保证不漏接 emit
 *   - backgroundScope 不行 — events.collect 永远不结束, 会让 runTest 卡住
 *   - 必须手动 cancel collector job
 *
 * 注意:
 *   - util.L 调 android.util.Log → 在 JVM 测试里被 isReturnDefaultValues=true 兜底成 no-op, 不崩
 *   - CombatEngine / DungeonGenerator 的随机性: 测试中尽量避免触发它们 (用空走廊地图)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorationEngineTest {

    // ─────────────────────────────────────────────────────────
    //  Setup helpers
    // ─────────────────────────────────────────────────────────

    private fun makeHero(
        id: String = "h1",
        name: String = id,
        heroClass: HeroClass = HeroClass.WARRIOR,
        hp: Int = 100, maxHp: Int = 100,
        mp: Int = 0, maxMp: Int = 0,
        str: Int = 50, dex: Int = 50, intl: Int = 10, fth: Int = 10,
        status: HeroStatus = HeroStatus.DEPLOYED,
        spells: List<HeroSpell> = emptyList()
    ): Hero = Hero(
        id = id, name = name, portraitUri = null,
        heroClass = heroClass, level = 5, exp = 0,
        currentHp = hp, maxHp = maxHp,
        currentMp = mp, maxMp = maxMp,
        baseStats = mapOf(
            StatType.STR to str, StatType.DEX to dex,
            StatType.INT to intl, StatType.FTH to fth
        ),
        status = status, spells = spells
    )

    /** 死胡同地图: 只有一格 STAIRS_UP, 四周全墙. BFS 必返回 null → 引擎立即退出 "无路可走". */
    private fun deadEndFloor(layer: Int = 1, width: Int = 15, height: Int = 15): DungeonFloor {
        val grid = Array(height) { Array(width) { DungeonNode(NodeType.WALL) } }
        grid[height / 2][width / 2] = DungeonNode(
            type = NodeType.STAIRS, event = NodeEvent.STAIRS_UP, explored = true
        )
        return DungeonFloor(layer, width, height, grid.map { it.toList() })
    }

    /**
     * 横向走廊地图.
     *   - 入口 STAIRS_UP @ (leftX, y); 右端 = 普通房间 (BFS 默认目标) 或自定义事件
     *   - 全格 explored=true, BFS 立刻能走
     *   - midEvents 用于在中间格塞陷阱/遭遇等事件
     */
    private fun corridorFloor(
        layer: Int = 1,
        leftX: Int = 4, rightX: Int = 10, y: Int = 7,
        width: Int = 15, height: Int = 15,
        rightEndEvent: NodeEvent = NodeEvent.NONE,
        midEvents: Map<Int, NodeEvent> = emptyMap()
    ): DungeonFloor {
        val grid = Array(height) { Array(width) { DungeonNode(NodeType.WALL) } }
        for (x in leftX..rightX) {
            val event = when (x) {
                leftX -> NodeEvent.STAIRS_UP
                rightX -> rightEndEvent
                else -> midEvents[x] ?: NodeEvent.NONE
            }
            val type = when (event) {
                NodeEvent.STAIRS_UP, NodeEvent.STAIRS_DOWN -> NodeType.STAIRS
                NodeEvent.BOSS_FIGHT -> NodeType.BOSS_ROOM
                else -> NodeType.CORRIDOR
            }
            val isRoom = x == leftX || x == rightX || x in midEvents.keys
            grid[y][x] = DungeonNode(
                type = type, event = event,
                roomNumber = if (isRoom) (x - leftX) else null,
                explored = true
            )
        }
        return DungeonFloor(layer, width, height, grid.map { it.toList() })
    }

    private fun makeSession(
        party: List<PartySlot> = listOf(PartySlot("h1", Position.FRONT)),
        currentSupply: Int = 10,
        carriedItems: List<String> = emptyList(),
        strategy: StrategyType = StrategyType.BALANCED,
        secondaryStrategy: SecondaryStrategyType = SecondaryStrategyType.EXPLORE,
        currentLayer: Int = 1
    ): ExpeditionSession = ExpeditionSession(
        dungeonId = "test_dungeon",
        currentLayer = currentLayer,
        party = party,
        currentSupply = currentSupply, maxSupply = currentSupply,
        carriedItems = carriedItems,
        strategy = strategy, secondaryStrategy = secondaryStrategy
    )

    /**
     * 跑一次完整探险, 同时订阅 ExpeditionEventBus.
     * 返回事件总线收到的事件列表 (按时序). 最终 state 通过 stateFlow.value 查.
     */
    private suspend fun TestScope.runExpedition(
        state: MutableStateFlow<GameUiState>,
        session: ExpeditionSession
    ): List<ExpeditionEvent> {
        val collected = mutableListOf<ExpeditionEvent>()
        // UnconfinedTestDispatcher 让 launch 立即进入 collect 状态, 不会漏接 emit
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            ExpeditionEventBus.events.collect { collected.add(it) }
        }
        ExplorationEngine().run(session, state)
        job.cancel()
        return collected
    }

    // =========================================================
    //  A. 启动 / 收尾基本流程
    // =========================================================

    @Test
    fun `运行结束后 isExploring 为 false`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession())
        assertEquals(false, state.value.expedition?.isExploring)
    }

    @Test
    fun `运行结束后出战英雄 status 变 IDLE`() = runTest {
        val hero = makeHero(status = HeroStatus.DEPLOYED)
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(hero),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession())
        assertEquals(HeroStatus.IDLE, state.value.heroes[0].status)
    }

    @Test
    fun `运行结束后 ExpeditionEventBus 至少发出一次 Retreated`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        val events = runExpedition(state, makeSession())
        assertTrue("应至少发出一次 Retreated 事件",
            events.any { it is ExpeditionEvent.Retreated })
    }

    @Test
    fun `启动时 posX posY 设为地图 STAIRS_UP 坐标`() = runTest {
        // deadEndFloor 把 STAIRS_UP 放在 (width/2, height/2) = (7, 7)
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession())
        // 死胡同地图 BFS 立即返回 null → 没动过, 终位置 = 起始 = (7, 7)
        assertEquals(7, state.value.expedition?.posX)
        assertEquals(7, state.value.expedition?.posY)
    }

    @Test
    fun `开场日志包含楼层提示`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor(layer = 3)
        ))
        runExpedition(state, makeSession(currentLayer = 3))
        val log = state.value.expedition?.combatLog ?: emptyList()
        assertTrue("should have an opening log line (Floor 3)",
            log.any { "Floor 3" in it })
    }

    // =========================================================
    //  B. 撤退原因 (reason)
    // =========================================================

    @Test
    fun `死胡同地图 - Retreated reason 为 无路可走`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        val events = runExpedition(state, makeSession())
        val retreated = events.filterIsInstance<ExpeditionEvent.Retreated>().last()
        assertTrue("reason '${retreated.reason}' should contain 'No path forward'", "No path forward" in retreated.reason)
    }

    @Test
    fun `走廊 supply 耗尽 - Retreated reason 为 补给耗尽`() = runTest {
        // 起始 supply=2, 走 1 步后 supply=1, 再走 1 步后 supply=0 → "补给耗尽"
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = corridorFloor()
        ))
        val events = runExpedition(state, makeSession(currentSupply = 2))
        val retreated = events.filterIsInstance<ExpeditionEvent.Retreated>().last()
        assertTrue("reason '${retreated.reason}' should contain 'Out of supplies'", "Out of supplies" in retreated.reason)
    }

    @Test
    fun `全员 HP 为 0 出战 - Retreated reason 为 全员重伤`() = runTest {
        // HP=0 时 isAlive=false. 主循环 BFS 走 1 步, 退出条件 ② 检查 all dead → "全员重伤"
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero(hp = 0, maxHp = 100)),
            currentFloor = corridorFloor()
        ))
        val events = runExpedition(state, makeSession(
            currentSupply = 20,
            strategy = StrategyType.AGGRESSIVE   // AGGRESSIVE 不会因战术先撤
        ))
        val retreated = events.filterIsInstance<ExpeditionEvent.Retreated>().last()
        assertTrue("reason '${retreated.reason}' should contain 'Party wiped'", "Party wiped" in retreated.reason)
    }

    // =========================================================
    //  C. 地图复用 vs 重新生成
    // =========================================================

    @Test
    fun `state currentFloor 已存在 - 引擎复用 不重新生成`() = runTest {
        // 用 layer=7 的特征地图, 验证最终 expedition.currentLayer 也是 7 (没被 session.currentLayer=1 盖掉)
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor(layer = 7)
        ))
        runExpedition(state, makeSession(currentLayer = 1))   // session 说 1 层, 地图说 7 层
        assertEquals(7, state.value.expedition?.currentLayer)
    }

    // =========================================================
    //  D. 补给消耗
    // =========================================================

    @Test
    fun `7 格走廊从左走到右 - 走 6 步 supply 减少 6`() = runTest {
        // 走廊 leftX=4 .. rightX=10 → 6 步走完
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = corridorFloor()
        ))
        runExpedition(state, makeSession(currentSupply = 10))
        // 走到末端后 BFS 再无目标 → "无路可走" 退出. 应该已走 6 步.
        val finalSupply = state.value.expedition?.currentSupply ?: -1
        assertEquals("走 6 格 + 每格 1 补给, 应剩 4", 4, finalSupply)
    }

    // =========================================================
    //  E. 战术撤退判断 (shouldRetreat)
    // =========================================================

    @Test
    fun `AGGRESSIVE 战术 - 低 HP 无回血手段也不主动撤退`() = runTest {
        // HP 10% + AGGRESSIVE + 死胡同 → 退出原因应是 "无路可走" 不是 "战术撤退"
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero(hp = 10, maxHp = 100)),
            currentFloor = deadEndFloor()
        ))
        val events = runExpedition(state, makeSession(strategy = StrategyType.AGGRESSIVE))
        val reason = events.filterIsInstance<ExpeditionEvent.Retreated>().last().reason
        assertFalse("AGGRESSIVE should not trigger a tactical retreat, but reason='$reason'",
            "Tactical retreat" in reason)
    }

    @Test
    fun `SAFE 战术 + 低 HP + 无回血手段 - 立即战术撤退`() = runTest {
        // HP 10% < 50% + 无血瓶 + 战士无治疗术 → SAFE shouldRetreat=true
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero(hp = 10, maxHp = 100)),
            currentFloor = corridorFloor()
        ))
        val events = runExpedition(state, makeSession(
            currentSupply = 20,   // 给足够补给, 排除 "补给耗尽" 干扰
            strategy = StrategyType.SAFE
        ))
        val reason = events.filterIsInstance<ExpeditionEvent.Retreated>().last().reason
        assertTrue("SAFE with low HP should trigger a tactical retreat, actual reason='$reason'",
            "Tactical retreat" in reason)
    }

    @Test
    fun `SAFE 战术 + 低 HP + 携带血瓶 - 不触发战术撤退`() = runTest {
        // 有血瓶 → shouldRetreat 立即 return false (有回血手段)
        // 后续退出应是别的原因 (补给耗尽 / 无路可走)
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero(hp = 10, maxHp = 100)),
            currentFloor = corridorFloor()
        ))
        val events = runExpedition(state, makeSession(
            currentSupply = 2,
            strategy = StrategyType.SAFE,
            carriedItems = listOf("health_potion")
        ))
        val reason = events.filterIsInstance<ExpeditionEvent.Retreated>().last().reason
        assertFalse("carrying a health potion should not trigger a tactical retreat, but reason='$reason'",
            "Tactical retreat" in reason)
    }

    // =========================================================
    //  F. 收尾副作用 — 仓库 / 状态归位
    // =========================================================

    @Test
    fun `结束后 未使用的 carriedItems 回到 inventory`() = runTest {
        // 死胡同 + 携 health_potion → 不会用到药水 → 收尾时回仓
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor(),
            inventory = emptyList()
        ))
        runExpedition(state, makeSession(carriedItems = listOf("health_potion")))
        assertEquals("inventory 应有 1 个回退的 health_potion",
            1, state.value.inventory.count { it.id == "health_potion" })
    }

    @Test
    fun `结束后 party 英雄 IDLE 非 party 英雄状态不变`() = runTest {
        val partyHero = makeHero(id = "h1", status = HeroStatus.DEPLOYED)
        val benchedHero = makeHero(id = "h2", status = HeroStatus.IDLE)
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(partyHero, benchedHero),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession(
            party = listOf(PartySlot("h1", Position.FRONT))
        ))
        val byId = state.value.heroes.associateBy { it.id }
        assertEquals(HeroStatus.IDLE, byId["h1"]?.status)
        assertEquals(HeroStatus.IDLE, byId["h2"]?.status)  // 未变
    }

    @Test
    fun `结束后 HP MP 不会自动回满 (重伤是惩罚)`() = runTest {
        // HP 30% 出战 → 死胡同立即退出 → HP 应保留 30 (不重置为 100)
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero(hp = 30, maxHp = 100, mp = 5, maxMp = 50)),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession())
        val finalHero = state.value.heroes[0]
        assertEquals("HP 应保留出战时的 30", 30, finalHero.currentHp)
        assertEquals("MP 应保留出战时的 5", 5, finalHero.currentMp)
    }

    // =========================================================
    //  G. 事件总线 / 完整性
    // =========================================================

    @Test
    fun `Retreated 永远是最后一个事件 (探险结束的标记)`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        val events = runExpedition(state, makeSession())
        assertTrue("events 非空", events.isNotEmpty())
        assertTrue("最后一个事件应是 Retreated, 实际是 ${events.last()::class.simpleName}",
            events.last() is ExpeditionEvent.Retreated)
    }

    @Test
    fun `运行过程不抛异常 (默认参数)`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = corridorFloor()
        ))
        runExpedition(state, makeSession())
        // 跑完不崩即通过
    }

    // =========================================================
    //  H. 进入新格子的位置更新
    // =========================================================

    @Test
    fun `走廊地图 - 终位置在右端 (BFS 一直向右走)`() = runTest {
        // corridorFloor 默认 leftX=4 rightX=10 y=7. 从 (4,7) 走到 (10,7).
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = corridorFloor()
        ))
        runExpedition(state, makeSession(currentSupply = 20))
        assertEquals(10, state.value.expedition?.posX)
        assertEquals(7, state.value.expedition?.posY)
    }

    // =========================================================
    //  I. expedition 字段保留
    // =========================================================

    @Test
    fun `结束后 expedition 字段仍存在 (不会被置空)`() = runTest {
        // 这是 UI 需要的: 探险结束后 UI 还要展示战利品/日志, 不能立即清掉 expedition
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession())
        assertNotNull("结束后 expedition 不应为 null", state.value.expedition)
    }

    @Test
    fun `currentFloor 在结束后仍保留 (用于 UI 显示最终地图)`() = runTest {
        val state = MutableStateFlow(GameUiState(
            heroes = listOf(makeHero()),
            currentFloor = deadEndFloor()
        ))
        runExpedition(state, makeSession())
        assertNotNull(state.value.currentFloor)
    }
}
