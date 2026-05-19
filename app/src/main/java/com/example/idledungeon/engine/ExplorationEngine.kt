package com.example.idledungeon.engine

import com.example.idledungeon.data.ItemRegistry
import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.data.SpellRegistry
import com.example.idledungeon.model.*
import com.example.idledungeon.notification.ExpeditionEvent
import com.example.idledungeon.notification.ExpeditionEventBus
import com.example.idledungeon.util.L
import com.example.idledungeon.viewmodel.advanceQuestsIfNeeded
import com.example.idledungeon.viewmodel.GameUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 探索引擎 — 协程驱动的"放置式"地牢探索.
 *
 * ─── 干什么 ───
 * 接到一次出战请求后, 自动操控队伍在地牢里走动 / 触发事件 / 战斗 / 拾取 / 升降楼,
 * 全程异步运行, UI 通过订阅 stateFlow 看到逐步推进 (类似自动战斗的回放).
 *
 * ─── 输入 / 输出 ───
 *   入: ExpeditionSession (队伍 + 战术 + 补给 + 携带物) + 共享 MutableStateFlow<GameUiState>
 *   出: 不直接 return — 全部副作用通过 stateFlow.update {} 写回 (HP/MP/背包/楼层/任务/图鉴)
 *
 * ─── 主流程 (run 协程) ───
 *   1. 准备阶段
 *      - 复用上次保存的 currentFloor; 没有就用 DungeonGenerator.generate() 造新地图
 *      - 在 STAIRS_UP 处放下队伍, 揭开初始视野 (战争迷雾)
 *   2. 主循环 (while isExploring && supply > 0)
 *      ① 主战术撤退检查 → 满足条件就主动撤退
 *      ② BFS 找下一步 (按副战术挑目标)
 *      ③ 走到下一格, 处理那一格的事件 (7 种):
 *           TRAP / ENCOUNTER / LOOT / PUZZLE / GUARDED_LOOT / BOSS_FIGHT / STAIRS_DOWN
 *      ④ 战后/陷阱后自动恢复 (牧师治疗 → 血瓶 → 蓝瓶 循环到没动作)
 *      ⑤ 扣补给, 写 stateFlow, 判断退出条件 (补给空 / 全员重伤)
 *   3. 收尾阶段
 *      - 队伍状态切回 IDLE (保留当前 HP/MP, 下次出战开始才回满)
 *      - 未用消耗品回仓库, 背包战利品入仓库
 *      - 通过 ExpeditionEventBus 通知系统层 (用于离线提醒)
 *
 * ─── 主战术 (StrategyType, 控制何时撤退) ───
 *   AGGRESSIVE: 永不撤退
 *   RISKY:      有人 HP < 20% 且无回血手段
 *   BALANCED:   有人 HP < 20% 或全队 HP 总和 < 50% 且无回血手段
 *   SAFE:       有人 HP < 50% 且无回血手段
 *
 * ─── 副战术 (SecondaryStrategyType, 控制 BFS 找路目标优先级) ───
 *   EXPLORE:    未探索房间 → 楼梯 → Boss
 *   SCAVENGE:   全部房间探索完才打 Boss / 下楼
 *   BOSS_RUSH:  Boss 永远最高优先
 *
 * ─── 持久化要点 ───
 *   - 走过的格子 visited 写在 DungeonNode 上 → 跨冒险保留 (地图复用时能看到上次足迹)
 *   - 视野 explored 同样写在节点上 → 战争迷雾不重置
 *   - 战斗后败北时 visited 会回滚 → 下次还能再尝试这个房间
 *
 * ─── 重要不变量 ───
 *   - HP=0 是"重伤 (injured)"不是阵亡, 战后队伍归城, HP 会保留到下次出战开始才回满
 *   - 任何状态变更都通过 stateFlow.update {} 原子更新, 不直接持引用修改 Hero
 */
class ExplorationEngine {

    companion object {
        /** 每步基础间隔 — 让 UI 能看清楚动画, 太快用户跟不上 */
        private const val BASE_INTERVAL_MS = 600L

        // ─── 补给消耗表 (走一格 1 点; 各事件在 1 基础上再叠加) ───
        private const val SUPPLY_STEP = 1                  // 默认: 走一格 = 1 点补给
        private const val SUPPLY_TRAP_DISARMED_EXTRA = 1   // 解陷阱成功 = 总 2 (1 走 + 1 额外)
        private const val SUPPLY_TRAP_TRIGGERED_EXTRA = 5  // 触发陷阱   = 总 6 (1 走 + 5 额外)
        private const val SUPPLY_PUZZLE = 3                // 解谜不论成败 固定 3
        private const val SUPPLY_COMBAT = 10               // 普通战斗 / 守护宝藏
        private const val SUPPLY_BOSS = 20                 // Boss 战 (惩罚跑 boss 的快感)
    }

    /** 本次冒险的临时访问记录, 给 BFS 用 (避免回头跑死循环). 跨冒险用 DungeonNode.visited 持久化. */
    private val visitedRooms = mutableSetOf<Pair<Int, Int>>()

    /**
     * 主入口 — 一次完整冒险的协程. 不抛异常 (异常通过 stateFlow 反映), 调用方 launch 即可.
     *
     * 这是个挺长的函数 (~350 行), 因为单局冒险流程紧密耦合. 拆分尝试过, 但分多个函数会让
     * state 在协程间传递更难懂 — 现在的写法虽然行数多, 顺读一遍逻辑很清晰.
     */
    suspend fun run(session: ExpeditionSession, stateFlow: MutableStateFlow<GameUiState>) {
        visitedRooms.clear()
        val state = stateFlow.value
        L.i("Expedition", "start — party=${session.party.size}, supply=${session.currentSupply}, " +
            "strategy=${session.strategy}/${session.secondaryStrategy}, carried=${session.carriedItems.size}")

        // ─── 1. 准备阶段: 决定使用哪张地图 ───
        // 优先复用上次冒险保留的地图 (玩家中途撤退过 — currentFloor 还在), 没有则按 layer 重新生成
        val savedFloor = state.currentFloor
        var currentFloor = savedFloor ?: DungeonGenerator.generate(session.currentLayer)
        val layer = currentFloor.layer   // 以地图为准 — session 可能是 1, 但地图可能存着上次的 3 层
        val startPos = findStairsUp(currentFloor)
        visitedRooms.add(startPos)
        currentFloor = revealVisibility(currentFloor, startPos.first, startPos.second)

        val resumed = savedFloor != null
        if (resumed) L.i("Expedition", "reusing saved map layer=$layer, start=$startPos")
        val openingLog = if (resumed)
            "== Floor $layer — continue exploring =="
        else
            "== Floor $layer — exploration begins =="

        var s = session.copy(
            isExploring = true,
            currentLayer = layer,
            posX = startPos.first, posY = startPos.second,
            combatLog = listOf(openingLog)          //直接把string扔进list，kotlin小巧思
        )
        stateFlow.update { it.copy(currentFloor = currentFloor, expedition = s) }
        delay(BASE_INTERVAL_MS)

        // 记录撤退原因, 结束时一次性 emit Retreated 事件 (用于通知系统)
        var retreatReason: String? = null

        // ─── 2. 主循环 ───
        while (s.isExploring && s.currentSupply > 0) {          //补给归零自动撤
            // ── 撤退判定 ──
            if (shouldRetreat(s, stateFlow)) {
                s = s.appendLog("")
                s = s.appendLog("Tactical retreat!")
                s = s.copy(isExploring = false)
                retreatReason = "Tactical retreat"
                stateFlow.update { it.copy(expedition = s) }
                break
            }

            val cx = s.posX; val cy = s.posY

            // ── BFS 找下一格 ──
            // 没有路可走 → 主动撤退 (理论上 BFS 至少能回头, 但如果回头格也走过了就回不去)
            val next = bfsNextStep(currentFloor, cx, cy, s.secondaryStrategy) ?: run {
                s = s.appendLog("")
                s = s.appendLog("No path forward, retreating...")
                s = s.copy(isExploring = false)
                retreatReason = "No path forward"
                stateFlow.update { it.copy(expedition = s) }
                break
            }
            val (nx, ny) = next
            val node = currentFloor.grid[ny][nx]

            val isRoom = node.isRoom
            // 必须在 markVisited 之前判定 — 之后 visited 就为 true 了
            val firstEntry = isRoom && !node.visited
            if (isRoom) visitedRooms.add(nx to ny)

            // 主线任务: 累计探索房间数 — 仅首次进入计数 (再次进入不重复算)
            if (firstEntry) {
                stateFlow.update { st ->
                    val gs = st.gameState.copy(roomsExplored = st.gameState.roomsExplored + 1)
                    advanceQuestsIfNeeded(st.copy(gameState = gs))
                }
            }

            currentFloor = revealVisibility(currentFloor, nx, ny)

            // 标记已到达 (UI 用这个高亮已访问房间)
            if (firstEntry) {
                currentFloor = markVisited(currentFloor, nx, ny)
            }

            // ── 房间显示名 (用于日志) ──
            val roomName = when {
                node.type == NodeType.BOSS_ROOM -> "Boss Room"
                node.event == NodeEvent.STAIRS_DOWN -> "Stairs"
                node.event == NodeEvent.STAIRS_UP -> "Entry"
                node.roomNumber != null -> "Room ${node.roomNumber}"
                else -> ""
            }

            val hasEvent = node.event != NodeEvent.NONE && node.event != NodeEvent.STAIRS_UP
            var supplyCost: Int = SUPPLY_STEP   // 默认走一格; 触发事件会被覆盖
            val log = mutableListOf<String>()

            // ── 处理本格事件 ──
            if (!hasEvent) {
                // 空格子 / 已清空的房间: 只在第一次进入时打个"平安无事"
                if (isRoom && node.event == NodeEvent.NONE && firstEntry) {
                    log.add("")
                    log.add("[ $roomName ] Entered — nothing here")
                }
            } else {
                val header = if (roomName.isNotEmpty()) "[ $roomName ]" else "[Corridor]"

                when (node.event) {
                    // ─── 陷阱: DEX 判定决定解除还是触发 ───
                    NodeEvent.TRAP -> {
                        log.add(""); log.add("$header TRAP!")
                        val aliveHeroes = getPartyHeroes(stateFlow.value, s.party).filter { it.isAlive }
                        val check = statCheck(StatType.DEX, aliveHeroes, s.currentLayer)
                        log.add("  Disarm check: highest DEX ${check.maxStat} vs difficulty ${check.difficulty} → ${check.chance}%")
                        if (check.passed) {
                            log.add("  Disarmed!")
                            supplyCost = SUPPLY_STEP + SUPPLY_TRAP_DISARMED_EXTRA  // 2
                        } else {
                            val trapDmg = 5 * s.currentLayer
                            // 仅对存活英雄结算 — 已倒下的人不会重复挨打 (也不写没意义的日志)
                            if (aliveHeroes.isNotEmpty()) {
                                val damaged = aliveHeroes.map { it.copy(currentHp = (it.currentHp - trapDmg).coerceAtLeast(0)) }
                                updateHeroes(stateFlow, damaged)
                                log.add("  Triggered! Surviving heroes take $trapDmg damage")
                                damaged.forEach { h -> log.add("  ${h.name}: HP ${h.currentHp}/${h.maxHp}") }
                            }
                            supplyCost = SUPPLY_STEP + SUPPLY_TRAP_TRIGGERED_EXTRA  // 6
                        }
                        currentFloor = clearEvent(currentFloor, nx, ny)
                    }
                    // ─── 普通遭遇战 ───
                    NodeEvent.ENCOUNTER -> {
                        supplyCost = SUPPLY_COMBAT
                        val heroes = getPartyHeroes(stateFlow.value, s.party)
                        if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                            log.add(""); log.add("$header ENCOUNTER!")
                            val result = CombatEngine.fight(
                                heroes, node.monsterIds, s.carriedItems,
                                partyPositions = s.party.associate { it.heroId to it.position }
                            )
                            val outcome = applyCombatResult(
                                result, s, currentFloor, nx, ny, stateFlow, log,
                                defeatLogMsg = "Party injured! Retreating...",
                                defeatReason = "Lost the encounter, party injured"
                            )
                            s = outcome.newSession
                            currentFloor = outcome.newFloor
                            outcome.retreatReason?.let { retreatReason = it }
                        }
                    }
                    // ─── 普通宝箱 (旧版本残留 — 新地图用 GUARDED_LOOT) ───
                    NodeEvent.LOOT -> {
                        supplyCost = SUPPLY_STEP   // 不算事件, 跟空格子一样
                        log.add(""); log.add("$header LOOT — found a chest!")
                        val loot = rollLoot(node.lootTable)
                        if (loot.isNotEmpty()) {
                            s = s.copy(backpack = s.backpack + loot)
                            log.add("  Obtained: ${itemNames(loot)}")
                        } else {
                            log.add("  Empty chest...")
                        }
                        currentFloor = clearEvent(currentFloor, nx, ny)
                    }
                    // ─── 解谜: INT 判定决定能否破解 ───
                    NodeEvent.PUZZLE -> {
                        supplyCost = SUPPLY_PUZZLE
                        log.add(""); log.add("$header PUZZLE!")
                        val aliveHeroes = getPartyHeroes(stateFlow.value, s.party).filter { it.isAlive }
                        val check = statCheck(StatType.INT, aliveHeroes, s.currentLayer)
                        log.add("  Solve check: highest INT ${check.maxStat} vs difficulty ${check.difficulty} → ${check.chance}%")
                        if (check.passed) {
                            val loot = rollLoot(node.lootTable)
                            if (loot.isNotEmpty()) {
                                s = s.copy(backpack = s.backpack + loot)
                                log.add("  Solved — obtained: ${itemNames(loot)}")
                            } else {
                                log.add("  Solved — but there was nothing inside...")
                            }
                        } else {
                            log.add("  Failed")
                        }
                        currentFloor = clearEvent(currentFloor, nx, ny)
                    }
                    // ─── 守护宝藏: 战斗 + 战胜后开宝箱 (含主线"苔纹剑"保底) ───
                    NodeEvent.GUARDED_LOOT -> {
                        supplyCost = SUPPLY_COMBAT
                        val heroes = getPartyHeroes(stateFlow.value, s.party)
                        if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                            log.add(""); log.add("$header GUARDED LOOT!")
                            val result = CombatEngine.fight(
                                heroes, node.monsterIds, s.carriedItems,
                                partyPositions = s.party.associate { it.heroId to it.position }
                            )
                            val outcome = applyCombatResult(
                                result, s, currentFloor, nx, ny, stateFlow, log,
                                defeatLogMsg = "Defeated! Retreating...",
                                defeatReason = "Lost the guarded-treasure fight"
                            )
                            s = outcome.newSession
                            currentFloor = outcome.newFloor
                            outcome.retreatReason?.let { retreatReason = it }

                            if (result.victory) {
                                log.add("Treasure unsealed!")
                                // ── 苔纹剑保底逻辑 ──
                                // F2 累计 5 次守护宝箱仍未掉剑 → 第 5 次强制掉, 防止永远刷不到主线物品
                                val swordEligible = s.currentLayer == 2 && QUEST_EPIC_SWORD_ID in node.lootTable
                                val haveSword = QUEST_EPIC_SWORD_ID in stateFlow.value.gameState.foundItemIds
                                    || QUEST_EPIC_SWORD_ID in s.backpack
                                val forceSword = swordEligible && !haveSword
                                    && stateFlow.value.gameState.f2ChestPity >= 4
                                // 这里不用 rollLoot, 因为要让保底剑强制掉.
                                // 顺便屏蔽"已持有玩家"再次自然掉到剑 — 史诗主线物品仅给一次, 多余的剑没意义.
                                val loot = node.lootTable.filter { (id, c) ->
                                    if (id == QUEST_EPIC_SWORD_ID && haveSword) false           // 已有剑 → 永不再掉
                                    else if (forceSword && id == QUEST_EPIC_SWORD_ID) true      // 保底 → 强制掉
                                    else kotlin.random.Random.nextInt(100) < c                  // 其他物品 → 正常 roll
                                }.keys.toList()
                                if (loot.isNotEmpty()) {
                                    s = s.copy(backpack = s.backpack + loot)
                                    log.add("  Obtained: ${itemNames(loot)}" +
                                        if (forceSword) "  [pity]" else "")
                                } else {
                                    log.add("  Empty chest...")
                                }
                                // 更新保底计数器 (掉到剑就重置为 0, 没掉就 +1)
                                if (swordEligible && !haveSword) {
                                    val gotSword = QUEST_EPIC_SWORD_ID in loot
                                    stateFlow.update { state ->
                                        state.copy(gameState = state.gameState.copy(
                                            f2ChestPity = if (gotSword) 0
                                                          else state.gameState.f2ChestPity + 1
                                        ))
                                    }
                                }
                            }
                        }
                    }
                    // ─── Boss 战 (+ 楼层进度推进 + 系统通知) ───
                    NodeEvent.BOSS_FIGHT -> {
                        supplyCost = SUPPLY_BOSS
                        val heroes = getPartyHeroes(stateFlow.value, s.party)
                        if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                            log.add(""); log.add("$header ★ BOSS BATTLE! ★")
                            val result = CombatEngine.fight(
                                heroes, node.monsterIds, s.carriedItems,
                                partyPositions = s.party.associate { it.heroId to it.position }
                            )
                            val outcome = applyCombatResult(
                                result, s, currentFloor, nx, ny, stateFlow, log,
                                defeatLogMsg = "Defeated by the Boss... party injured!",
                                defeatReason = "Defeated by the Boss"
                            )
                            s = outcome.newSession
                            currentFloor = outcome.newFloor
                            outcome.retreatReason?.let { retreatReason = it }

                            if (result.victory) {
                                log.add("★ Boss defeated! ★")
                                // 通知系统层 (用于离线推送 — 后台 App 也能弹通知)
                                val bossName = node.monsterIds.firstOrNull()?.let {
                                    try { MonsterRegistry.spawn(it).name } catch (_: Exception) { it }
                                } ?: "Boss"
                                L.i("Expedition", "Boss defeated! layer=${s.currentLayer}, name=$bossName")
                                ExpeditionEventBus.emit(ExpeditionEvent.BossDefeated(bossName, s.currentLayer))
                                // 推进主线任务: 该层 boss 加入已击杀集合
                                val defeatedLayer = s.currentLayer
                                stateFlow.update { st ->
                                    val gs = st.gameState.copy(
                                        bossesDefeated = st.gameState.bossesDefeated + defeatedLayer
                                    )
                                    advanceQuestsIfNeeded(st.copy(gameState = gs))
                                }
                            }
                        }
                    }
                    // ─── 下楼梯: 生成新楼层, 把队伍放过去 ───
                    NodeEvent.STAIRS_DOWN -> {
                        supplyCost = SUPPLY_STEP
                        log.add(""); log.add("$header >> STAIRS DOWN — found descending stairs!")
                        val newLayer = s.currentLayer + 1
                        L.i("Expedition", "descend: layer ${s.currentLayer} → $newLayer (generate new map)")
                        // 注: entryX/entryY 用旧楼下楼梯位置, DungeonGenerator 内 coerceIn 兜底
                        currentFloor = DungeonGenerator.generate(newLayer, entryX = nx, entryY = ny)
                        visitedRooms.clear()
                        val newStart = findStairsUp(currentFloor)           //给我改了
                        visitedRooms.add(newStart)
                        currentFloor = revealVisibility(currentFloor, newStart.first, newStart.second)
                        s = s.copy(currentLayer = newLayer, posX = newStart.first, posY = newStart.second)
                        log.add(""); log.add("== Floor $newLayer — exploration begins ==")
                        ExpeditionEventBus.emit(ExpeditionEvent.FloorChanged(newLayer))
                    }
                    else -> { supplyCost = SUPPLY_STEP }
                }

                // ── 战后/陷阱后自动恢复 (持续到没动作可执行) ──
                val isDamageEvent = node.event == NodeEvent.TRAP ||
                    node.event == NodeEvent.ENCOUNTER ||
                    node.event == NodeEvent.GUARDED_LOOT ||
                    node.event == NodeEvent.BOSS_FIGHT
                if (s.isExploring && isDamageEvent) {
                    val (newSession, recoveryLog) = postEventRecovery(s, stateFlow)
                    s = newSession
                    if (recoveryLog.isNotEmpty()) {
                        log.add("")
                        log.add("[ Recovery ]")
                        log.addAll(recoveryLog)
                    }
                }
            }

            // ── 步进: 更新位置 / 扣补给 / 追加日志 / 写 stateFlow ──
            s = s.copy(
                posX = nx, posY = ny,
                currentSupply = (s.currentSupply - supplyCost).coerceAtLeast(0),
                combatLog = s.combatLog + log
            )
            stateFlow.update {
                it.copy(currentFloor = currentFloor, expedition = s,
                    gameState = it.gameState.copy(deepestFloorReached = maxOf(it.gameState.deepestFloorReached, s.currentLayer)))
            }

            // ── 退出条件 ①: 补给空了 ──
            if (s.currentSupply <= 0) {
                s = s.appendLog(""); s = s.appendLog("Out of supplies! Retreating...")
                s = s.copy(isExploring = false)
                retreatReason = retreatReason ?: "Out of supplies"
                stateFlow.update { it.copy(expedition = s) }; break
            }
            // ── 退出条件 ②: 全员重伤 ──
            val alive = getPartyHeroes(stateFlow.value, s.party)
            if (alive.none { it.isAlive }) {
                s = s.appendLog(""); s = s.appendLog("All heroes injured!")
                s = s.copy(isExploring = false)
                retreatReason = retreatReason ?: "Party wiped"
                stateFlow.update { it.copy(expedition = s) }; break
            }

            // 走空格快 (1/3 间隔), 触发事件用全速间隔 — 让 UI 节奏有起伏，花里胡哨
            val stepDelay = if (!hasEvent) BASE_INTERVAL_MS / 3 else BASE_INTERVAL_MS
            delay(stepDelay)
        }

        // ─── 3. 收尾阶段 ───
        if (s.isExploring) s = s.copy(isExploring = false)
        L.i("Expedition", "expedition end: reason=${retreatReason ?: "normal end"}, layer=${s.currentLayer}, " +
            "loot=${s.backpack.size}")
        ExpeditionEventBus.emit(ExpeditionEvent.Retreated(retreatReason ?: "Expedition ended"))
        s = s.appendLog("")
        s = s.appendLog("== Expedition ended ==")
        s = s.appendLog(if (s.backpack.isNotEmpty()) "Loot: ${itemNames(s.backpack)}" else "No loot")
        // 状态归 IDLE, HP/MP 保留撤退时数据 → 下次出战开始才回满 (有惩罚但不至于一直废)
        markPartyIdle(stateFlow, s.party)
        s = s.appendLog("Party returned to town")
        returnUnusedItems(stateFlow, s.carriedItems)
        transferBackpackToInventory(stateFlow, s.backpack)
        stateFlow.update { it.copy(expedition = s) }
    }

    // ═══════════════════════════════════════════
    //  战后恢复 (战斗/陷阱事件之后自动用药 + 治疗)
    // ═══════════════════════════════════════════

    /**
     * 循环执行三种恢复动作直到没有任何动作可执行:
     *   ① 牧师施治疗术 (HP < threshold 且 MP 够时)
     *   ② 喝生命药水 (牧师没法治时)
     *   ③ 喝魔法药水 (任意有 MP 亏空的英雄)
     *
     * threshold 由主战术决定: SAFE 0.75, 其它 0.50.
     * safety++ < 30 是死循环保险绳 — 万一某次状态没更新对, 最多跑 30 轮就退出.
     */
    private fun postEventRecovery(
        session: ExpeditionSession,
        stateFlow: MutableStateFlow<GameUiState>
    ): Pair<ExpeditionSession, List<String>> {
        val hpThreshold = if (session.strategy == StrategyType.SAFE) 0.75f else 0.50f
        val carriedItems = session.carriedItems.toMutableList()
        val log = mutableListOf<String>()

        var safety = 0
        while (safety++ < 30) {
            var anyAction = false

            // —— HP 恢复 (优先用牧师法术省蓝瓶) ——
            val state = stateFlow.value
            val heroes = getPartyHeroes(state, session.party).filter { it.isAlive }
            val needsHeal = heroes.any { it.currentHp.toFloat() / it.maxHp < hpThreshold }
            if (needsHeal) {
                if (tryClericHealOnce(session.party, stateFlow, log, hpThreshold)) {
                    anyAction = true
                } else if (tryHealthPotionOnce(session.party, stateFlow, carriedItems, log, hpThreshold)) {
                    anyAction = true
                }
            }

            // —— MP 恢复 (始终跑, 让牧师能继续治疗) ——
            if (tryManaPotionOnce(session.party, stateFlow, carriedItems, log)) {
                anyAction = true
            }

            if (!anyAction) break   // 无动作 = 都满血/没瓶/没蓝 → 结束
        }
        return session.copy(carriedItems = carriedItems.toList()) to log
    }

    /**
     * 牧师施一次治疗术 — 在所有学过的治疗法术里选 MP 够且威力最大的.
     * HEAL_SINGLE: 治最危险 (HP 百分比最低) 的人. HEAL_AOE: 全员雨露均沾.
     */
    private fun tryClericHealOnce(
        party: List<PartySlot>,
        stateFlow: MutableStateFlow<GameUiState>,
        log: MutableList<String>,
        threshold: Float
    ): Boolean {
        val state = stateFlow.value
        val heroes = getPartyHeroes(state, party).filter { it.isAlive }
        if (heroes.none { it.currentHp.toFloat() / it.maxHp < threshold }) return false

        // 找一个能放治疗术 (且 MP 够) 的牧师
        val cleric = heroes.firstOrNull { hero ->
            hero.heroClass == HeroClass.CLERIC && hero.spells.any {
                val t = SpellRegistry.get(it.templateId)
                (t.target == SpellTarget.HEAL_SINGLE || t.target == SpellTarget.HEAL_AOE) &&
                    hero.currentMp >= t.tiers[it.currentTier - 1].mpCost
            }
        } ?: return false

        // 在他所有治疗术中挑威力最高的
        val healSpells = cleric.spells.mapNotNull { spell ->
            val tmpl = SpellRegistry.get(spell.templateId)
            if (tmpl.target != SpellTarget.HEAL_SINGLE && tmpl.target != SpellTarget.HEAL_AOE) return@mapNotNull null
            val tier = tmpl.tiers[spell.currentTier - 1]
            if (cleric.currentMp >= tier.mpCost) tmpl to tier else null
        }
        val (tmpl, tier) = healSpells.maxByOrNull { it.second.power } ?: return false

        val statBonus = (cleric.effectiveStats[StatType.FTH] ?: 0) / 2
        val healAmt = tier.power + statBonus

        // 一次治疗可能改多个英雄 (AOE), 用 map 累积变更, 最后一并写回 stateFlow
        val updates = mutableMapOf<String, Hero>()
        val newCleric = cleric.copy(currentMp = cleric.currentMp - tier.mpCost)
        updates[cleric.id] = newCleric
        log.add("  ${cleric.name} casts [${tier.name}] (MP:${cleric.currentMp}→${newCleric.currentMp})")

        if (tmpl.target == SpellTarget.HEAL_SINGLE) {
            val patient = heroes
                .filter { it.currentHp.toFloat() / it.maxHp < threshold }
                .minByOrNull { it.currentHp.toFloat() / it.maxHp } ?: return false
            val baseHero = updates[patient.id] ?: patient
            val newHp = (baseHero.currentHp + healAmt).coerceAtMost(baseHero.maxHp)
            updates[patient.id] = baseHero.copy(currentHp = newHp)
            log.add("    → ${patient.name}: +$healAmt HP (HP:$newHp/${baseHero.maxHp})")
        } else {
            for (h in heroes) {
                val baseHero = updates[h.id] ?: h
                val newHp = (baseHero.currentHp + healAmt).coerceAtMost(baseHero.maxHp)
                updates[h.id] = baseHero.copy(currentHp = newHp)
                log.add("    → ${h.name}: +$healAmt HP (HP:$newHp/${baseHero.maxHp})")
            }
        }

        stateFlow.update { st -> st.copy(heroes = st.heroes.map { updates[it.id] ?: it }) }
        return true
    }

    /**
     * 喝一次生命药水给"最危险的英雄". 选瓶逻辑:
     *   优先用刚好够恢复一半亏空的最小瓶 (省瓶), 没合适的就用最小瓶兜底.
     */
    private fun tryHealthPotionOnce(
        party: List<PartySlot>,
        stateFlow: MutableStateFlow<GameUiState>,
        carriedItems: MutableList<String>,
        log: MutableList<String>,
        threshold: Float
    ): Boolean {
        val state = stateFlow.value
        val heroes = getPartyHeroes(state, party).filter { it.isAlive }
        val patient = heroes
            .filter { it.currentHp.toFloat() / it.maxHp < threshold }
            .minByOrNull { it.currentHp.toFloat() / it.maxHp } ?: return false

        val potions = carriedItems.distinct().mapNotNull {
            try { ItemRegistry.get(it).takeIf { i -> i.restoreHp > 0 } } catch (_: Exception) { null }
        }.sortedBy { it.restoreHp }
        if (potions.isEmpty()) return false

        // 至少能吸收瓶子一半的药效就喝, 否则浪费太多
        val deficit = patient.maxHp - patient.currentHp
        val potion = potions.firstOrNull { deficit >= (it.restoreHp + 1) / 2 } ?: potions.first()

        carriedItems.remove(potion.id)
        val newHp = (patient.currentHp + potion.restoreHp).coerceAtMost(patient.maxHp)
        log.add("  ${patient.name} uses ${potion.name} → HP ${patient.currentHp}→$newHp")
        stateFlow.update { st ->
            st.copy(heroes = st.heroes.map { if (it.id == patient.id) it.copy(currentHp = newHp) else it })
        }
        return true
    }

    /**
     * 喝一次魔法药水. 选瓶逻辑同血瓶 — 至少吸收一半才喝.
     * 历史 bug: 旧版本 `currentMp < restore` 在 maxMp < restore 时永远 true → 满 MP 也喝, 把蓝瓶清光.
     *           现在改成"亏空 >= 0.5×药效"才喝, 完美避免.
     */
    private fun tryManaPotionOnce(
        party: List<PartySlot>,
        stateFlow: MutableStateFlow<GameUiState>,
        carriedItems: MutableList<String>,
        log: MutableList<String>
    ): Boolean {
        val state = stateFlow.value
        val heroes = getPartyHeroes(state, party).filter { it.isAlive && it.maxMp > 0 }
        if (heroes.isEmpty()) return false

        val potions = carriedItems.distinct().mapNotNull {
            try { ItemRegistry.get(it).takeIf { i -> i.restoreMp > 0 } } catch (_: Exception) { null }
        }.sortedBy { it.restoreMp }
        if (potions.isEmpty()) return false

        // 小瓶优先 (减少浪费). 找一个亏空足够吸收一半瓶子的英雄给他喝.
        for (potion in potions) {
            val needed = (potion.restoreMp + 1) / 2
            val candidate = heroes
                .filter { (it.maxMp - it.currentMp) >= needed }
                .maxByOrNull { it.maxMp - it.currentMp } ?: continue
            carriedItems.remove(potion.id)
            val newMp = (candidate.currentMp + potion.restoreMp).coerceAtMost(candidate.maxMp)
            log.add("  ${candidate.name} uses ${potion.name} → MP ${candidate.currentMp}→$newMp")
            stateFlow.update { st ->
                st.copy(heroes = st.heroes.map { if (it.id == candidate.id) it.copy(currentMp = newMp) else it })
            }
            return true
        }
        return false
    }

    // ═══════════════════════════════════════════
    //  主战术: 撤退判定
    // ═══════════════════════════════════════════

    /**
     * 每轮主循环开头调一次, 判断是否该撤退. true = 撤退, false = 继续探索.
     * 通用规则: 有"回血手段" (血瓶 或 牧师当前能放治疗术) 就**永不撤退** — 撑得住.
     * 没回血手段时才按战术阈值检查.
     */
    private fun shouldRetreat(session: ExpeditionSession, stateFlow: MutableStateFlow<GameUiState>): Boolean {
        if (session.strategy == StrategyType.AGGRESSIVE) return false   // 一往无前模式直接跳过

        val heroes = getPartyHeroes(stateFlow.value, session.party).filter { it.isAlive }
        if (heroes.isEmpty()) return false   // 没人活着, 外层主循环会处理

        // 检查能否自救 (血瓶 / 治疗术)
        val hasHealPotion = session.carriedItems.any { id ->
            try { ItemRegistry.get(id).restoreHp > 0 } catch (_: Exception) { false }
        }
        val hasHealSpell = heroes.any { hero ->
            val canCast = hero.heroClass == HeroClass.CLERIC
            canCast && hero.spells.any { spell ->
                val t = com.example.idledungeon.data.SpellRegistry.get(spell.templateId)
                (t.target == SpellTarget.HEAL_SINGLE || t.target == SpellTarget.HEAL_AOE) &&
                    hero.currentMp >= t.tiers[spell.currentTier - 1].mpCost
            }
        }
        if (hasHealPotion || hasHealSpell) return false

        // 没回血手段 — 按战术阈值检查
        val anyBelow50 = heroes.any { it.currentHp.toFloat() / it.maxHp < 0.5f }
        val anyBelow20 = heroes.any { it.currentHp.toFloat() / it.maxHp < 0.2f }
        val totalHpRatio = heroes.sumOf { it.currentHp }.toFloat() / heroes.sumOf { it.maxHp }

        return when (session.strategy) {
            StrategyType.SAFE -> anyBelow50                                // 有人 <50% 撤
            StrategyType.BALANCED -> anyBelow20 || totalHpRatio < 0.5f     // 有人 <20% 或全队均血 <50% 撤
            StrategyType.RISKY -> anyBelow20                               // 有人 <20% 才撤
            StrategyType.AGGRESSIVE -> false                               // 上面已 return, 这里防御
        }
    }

    // ═══════════════════════════════════════════
    //  BFS 寻路
    // ═══════════════════════════════════════════

    /**
     * BFS 找下一步该往哪走. 返回相邻格 (cx,cy) 朝目标方向走的下一格.
     *
     * 算法:
     *   1. 标准 BFS 从 (cx,cy) 出发, 只穿越**已揭示且非墙**的格子
     *   2. 边走边记录 4 种"潜在目标": 最近的未探索房间 / 未探索拐角 / 未击败 Boss / 下楼梯
     *   3. 副战术决定怎么挑目标 (EXPLORE/SCAVENGE/BOSS_RUSH)
     *   4. prev 表回溯出"下一步该走哪格"
     *
     * "拐角 (frontier)" 是地图边界 — 已揭示的格子, 但旁边有未揭示的非墙格.
     * 这帮 BFS 在还没看见的地方也能往前推进, 不会卡在已知范围内.
     */
    private fun bfsNextStep(floor: DungeonFloor, cx: Int, cy: Int, strategy: SecondaryStrategyType): Pair<Int, Int>? {
        val queue = ArrayDeque<Pair<Int, Int>>()
        val prev = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()   // (子) → (父), 用于回溯路径
        val seen = mutableSetOf(cx to cy)
        queue.add(cx to cy)

        // 4 种潜在目标, 都是"最近的一个" (BFS 性质保证先发现即最近)
        var nearestRoom: Pair<Int, Int>? = null
        var nearestFrontier: Pair<Int, Int>? = null
        var nearestBoss: Pair<Int, Int>? = null
        var nearestStairs: Pair<Int, Int>? = null

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (x != cx || y != cy) {   // 跳过出发点 — 自己别当目标
                val node = floor.grid[y][x]

                // —— 未探索房间 (有 roomNumber 且 visited=false, 不是 Boss/楼梯) ——
                val isUnexploredRoom = node.roomNumber != null && !node.visited
                    && node.type == NodeType.CORRIDOR
                    && node.event != NodeEvent.BOSS_FIGHT && node.event != NodeEvent.STAIRS_DOWN
                if (isUnexploredRoom && nearestRoom == null) nearestRoom = x to y

                // —— 未击败 Boss ——
                if (node.type == NodeType.BOSS_ROOM && node.event == NodeEvent.BOSS_FIGHT
                    && (x to y) !in visitedRooms && nearestBoss == null) {
                    nearestBoss = x to y
                }

                // —— 下楼梯 ——
                if (node.event == NodeEvent.STAIRS_DOWN && (x to y) !in visitedRooms && nearestStairs == null) {
                    nearestStairs = x to y
                }

                // —— 拐角: 当前格已揭示, 但 4 邻居中有未揭示的非墙格 ——
                if (nearestFrontier == null) {
                    val isFrontier = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0).any { (dx, dy) ->
                        val fx = x + dx; val fy = y + dy
                        fy in floor.grid.indices && fx in floor.grid[0].indices
                            && !floor.grid[fy][fx].explored && floor.grid[fy][fx].type != NodeType.WALL
                    }
                    if (isFrontier) nearestFrontier = x to y
                }
            }
            // 标准 BFS 扩展 — 4 邻居, 只走已揭示的非墙格
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
                val fx = x + dx; val fy = y + dy
                if (fy !in floor.grid.indices || fx !in floor.grid[0].indices) continue
                if ((fx to fy) in seen) continue
                val adj = floor.grid[fy][fx]
                if (adj.type == NodeType.WALL || !adj.explored) continue
                seen.add(fx to fy)
                prev[fx to fy] = x to y
                queue.add(fx to fy)
            }
        }

        // ── 按副战术挑目标 ──
        val hasUnexplored = nearestRoom != null || nearestFrontier != null

        val target = when (strategy) {
            SecondaryStrategyType.EXPLORE -> {
                // 看见楼梯就赶紧下 (除非还有未探索的); 否则探索 > 楼梯 > Boss
                if (nearestStairs != null && !hasUnexplored) nearestStairs
                else nearestRoom ?: nearestFrontier ?: nearestStairs ?: nearestBoss
            }
            SecondaryStrategyType.SCAVENGE -> {
                // 把所有房间走完再打 Boss, 打完才下楼
                if (hasUnexplored) nearestRoom ?: nearestFrontier
                else nearestBoss ?: nearestStairs
            }
            SecondaryStrategyType.BOSS_RUSH -> {
                // Boss 永远最优先, 没 Boss 才搞别的
                nearestBoss ?: nearestRoom ?: nearestFrontier ?: nearestStairs
            }
        } ?: return null

        // 从 target 回溯到 (cx, cy) 的"第一步" — prev 表里一直找父, 找到父就是出发点为止
        var step = target
        while (true) {
            val p = prev[step] ?: return step
            if (p.first == cx && p.second == cy) return step
            step = p
        }
    }

    // ═══════════════════════════════════════════
    //  战争迷雾 — 视野揭示
    // ═══════════════════════════════════════════

    /**
     * 玩家走到 (px, py) 时揭开周围视野. 算法:
     *   1. 从 (px, py) 朝 4 个正方向射线扫描, 直到撞墙/房间/Boss房才停
     *   2. 把扫描到的每个格子, 连同它的 8 邻居 (3×3) 都标为已揭示
     * 效果: 站在房间里能看到整个房间; 站在走廊能看到走廊两端 + 走廊两侧.
     */
    private fun revealVisibility(floor: DungeonFloor, px: Int, py: Int): DungeonFloor {
        val visible = mutableSetOf(px to py)
        // 射线扫描 — 沿 4 个正方向往外打, 遇到墙/房间挡住就停
        for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
            var i = 1
            while (true) {
                val nx = px + dx * i; val ny = py + dy * i
                if (ny !in floor.grid.indices || nx !in floor.grid[0].indices) break
                val node = floor.grid[ny][nx]
                visible.add(nx to ny)
                if (node.type == NodeType.WALL || node.type == NodeType.BOSS_ROOM) break
                if (node.type == NodeType.CORRIDOR && node.roomNumber != null) break  // 房间格也挡视野
                i++
            }
        }
        // 扩散一圈 — 每个可见格的 3×3 邻居也算可见 (墙也能看到, 才有"地图轮廓"感)
        val toReveal = mutableSetOf<Pair<Int, Int>>()
        for ((vx, vy) in visible) {
            for (d2 in -1..1) for (d1 in -1..1) {
                val rx = vx + d1; val ry = vy + d2
                if (ry in floor.grid.indices && rx in floor.grid[0].indices) toReveal.add(rx to ry)
            }
        }
        return floor.copy(grid = floor.grid.mapIndexed { y, row ->
            row.mapIndexed { x, node ->
                if ((x to y) in toReveal && !node.explored) node.copy(explored = true) else node
            }
        })
    }

    // ═══════════════════════════════════════════
    //  事件处理通用 helpers
    // ═══════════════════════════════════════════

    /** 属性判定结果 — maxStat / 难度 / 命中率 / 是否过了. */
    private data class StatCheckResult(
        val maxStat: Int,
        val difficulty: Int,
        val chance: Int,
        val passed: Boolean
    )

    /**
     * 通用属性判定. TRAP 用 DEX, PUZZLE 用 INT, 公式相同:
     *   chance = (英雄最高属性 - 楼层×8) × 5 + 50, 夹在 [20, 95]%
     * 楼层越深难度越高, 单次 roll 0-99 比 chance 来定成败.
     */
    private fun statCheck(stat: StatType, aliveHeroes: List<Hero>, layer: Int): StatCheckResult {
        val maxStat = aliveHeroes.maxOfOrNull { it.effectiveStats[stat] ?: 0 } ?: 0
        val difficulty = layer * 8
        val chance = ((maxStat - difficulty) * 5 + 50).coerceIn(20, 95)
        val passed = kotlin.random.Random.nextInt(100) < chance
        return StatCheckResult(maxStat, difficulty, chance, passed)
    }

    /** 按掉落表 roll 物品 ID 列表. 每个 ID 独立 roll, 概率是 lootTable 的 value (0-100). */
    private fun rollLoot(lootTable: Map<String, Int>): List<String> =
        lootTable.filter { (_, c) -> kotlin.random.Random.nextInt(100) < c }.keys.toList()

    /** 把物品 ID 列表渲染成展示名 (查不到就退回 ID), 避免日志里出现下划线裸 ID. */
    private fun itemNames(ids: List<String>): String =
        ids.joinToString { id -> try { ItemRegistry.get(id).name } catch (_: Exception) { id } }

    /** 战斗结果应用 — 把 CombatEngine.fight() 返回的 result 落地到 session/floor/state. */
    private data class CombatOutcome(
        val newSession: ExpeditionSession,
        val newFloor: DungeonFloor,
        /** 失败时 = 撤退原因; 胜利时 = null. 调用方覆盖 retreatReason 用 */
        val retreatReason: String?
    )

    /**
     * 把 CombatEngine 的结果统一应用到状态:
     *   公共: 更新英雄 / 累计图鉴 / 倒入战利品 / 写回背包+消耗品
     *   胜利: 清掉本格事件, 返回 retreatReason=null
     *   失败: 写撤退日志, 回滚 visited 标记, 关闭探索, 返回 defeatReason
     *
     * 各事件分支的"胜利时额外动作" (Boss 通知 / 苔纹剑保底) 由调用方自己接着写,
     * 不放进 helper — 这些和"事件结算"语义无关.
     */
    private fun applyCombatResult(
        result: CombatEngine.CombatResult,
        session: ExpeditionSession,
        floor: DungeonFloor,
        nx: Int, ny: Int,
        stateFlow: MutableStateFlow<GameUiState>,
        log: MutableList<String>,
        defeatLogMsg: String,
        defeatReason: String
    ): CombatOutcome {
        log.addAll(result.log)
        updateHeroes(stateFlow, result.updatedHeroes)
        recordKills(stateFlow, result.killedMonsterIds)
        val updatedSession = session.copy(
            backpack = session.backpack + result.droppedItems,
            carriedItems = result.remainingCarriedItems
        )
        return if (result.victory) {
            CombatOutcome(updatedSession, clearEvent(floor, nx, ny), null)
        } else {
            log.add(defeatLogMsg)
            visitedRooms.remove(nx to ny)
            CombatOutcome(
                newSession = updatedSession.copy(isExploring = false),
                newFloor = unmarkVisited(floor, nx, ny),
                retreatReason = defeatReason
            )
        }
    }

    // ═══════════════════════════════════════════
    //  Helpers — 都是不可变 grid 的小修改 + ViewModel 同步
    // ═══════════════════════════════════════════

    /** 标记格子已访问 (visited=true). 用于 UI 高亮 + BFS 跳过重复房间. */
    private fun markVisited(floor: DungeonFloor, x: Int, y: Int): DungeonFloor =
        floor.copy(grid = floor.grid.mapIndexed { gy, row ->
            row.mapIndexed { gx, n -> if (gx == x && gy == y && !n.visited) n.copy(visited = true) else n }
        })

    /** 撤销 visited 标记 — 战斗失败时调用, 让该房间下次还能挑战. */
    private fun unmarkVisited(floor: DungeonFloor, x: Int, y: Int): DungeonFloor =
        floor.copy(grid = floor.grid.mapIndexed { gy, row ->
            row.mapIndexed { gx, n -> if (gx == x && gy == y && n.visited) n.copy(visited = false) else n }
        })

    /** 清空格子上的事件 (战斗胜利/陷阱触发后调用), 怪物/战利品都清掉. */
    private fun clearEvent(floor: DungeonFloor, x: Int, y: Int): DungeonFloor =
        floor.copy(grid = floor.grid.mapIndexed { gy, row ->
            row.mapIndexed { gx, n ->
                if (gx == x && gy == y) n.copy(event = NodeEvent.NONE, monsterIds = emptyList(), lootTable = emptyMap()) else n
            }
        })

    /** 找地图上的上楼梯位置 (= 入口). 找不到就退到任意非墙格 (兜底, 理论不应发生). */
    private fun findStairsUp(floor: DungeonFloor): Pair<Int, Int> {
        for (y in floor.grid.indices) for (x in floor.grid[y].indices)
            if (floor.grid[y][x].event == NodeEvent.STAIRS_UP) return x to y
        for (y in floor.grid.indices) for (x in floor.grid[y].indices)
            if (floor.grid[y][x].type != NodeType.WALL) return x to y
        return 0 to 0
    }

    /** 把 PartySlot 列表里的 heroId 翻成完整 Hero 实例 (从当前 state.heroes 查表). */
    private fun getPartyHeroes(state: GameUiState, party: List<PartySlot>): List<Hero> =
        party.mapNotNull { slot -> state.heroes.find { it.id == slot.heroId } }

    /** 累加本场战斗击杀到全局图鉴 (monsterKills: Map<怪物ID, 累计击杀数>) */
    private fun recordKills(stateFlow: MutableStateFlow<GameUiState>, killedIds: List<String>) {
        if (killedIds.isEmpty()) return
        stateFlow.update { state ->
            val newKills = state.gameState.monsterKills.toMutableMap()
            for (id in killedIds) newKills[id] = (newKills[id] ?: 0) + 1
            state.copy(gameState = state.gameState.copy(monsterKills = newKills))
        }
    }

    /** 把 CombatEngine 返回的更新后英雄列表合并回 stateFlow.heroes (按 id 匹配). */
    private fun updateHeroes(stateFlow: MutableStateFlow<GameUiState>, updatedHeroes: List<Hero>) {
        stateFlow.update { state ->
            val map = updatedHeroes.associateBy { it.id }
            state.copy(heroes = state.heroes.map { map[it.id] ?: it })
        }
    }

    /** 探索结束: 把队伍中的英雄状态切回 IDLE. HP/MP 保留撤退时的数值. */
    private fun markPartyIdle(stateFlow: MutableStateFlow<GameUiState>, party: List<PartySlot>) {
        val ids = party.map { it.heroId }.toSet()
        stateFlow.update { state ->
            state.copy(heroes = state.heroes.map { h ->
                if (h.id in ids) h.copy(status = HeroStatus.IDLE) else h
            })
        }
    }

    /** 把背包 (本次冒险拾取) 倒入仓库 inventory, 顺便推进 FIND_ITEM 类任务. */
    private fun transferBackpackToInventory(stateFlow: MutableStateFlow<GameUiState>, backpack: List<String>) {
        if (backpack.isEmpty()) return
        stateFlow.update { state ->
            val items = backpack.mapNotNull { id -> try { ItemRegistry.get(id) } catch (_: Exception) { null } }
            // foundItemIds 是历史拿到过的物品集合 — 给图鉴/任务用
            val newGameState = state.gameState.copy(
                foundItemIds = state.gameState.foundItemIds + backpack.toSet()
            )
            advanceQuestsIfNeeded(state.copy(
                inventory = state.inventory + items,
                gameState = newGameState
            ))
        }
    }

    /** 把没用完的消耗品 (carriedItems) 退回仓库. 出战前会重新从仓库扣. */
    private fun returnUnusedItems(stateFlow: MutableStateFlow<GameUiState>, remaining: List<String>) {
        if (remaining.isEmpty()) return
        stateFlow.update { state ->
            val items = remaining.mapNotNull { id -> try { ItemRegistry.get(id) } catch (_: Exception) { null } }
            state.copy(inventory = state.inventory + items)
        }
    }

    /** 给 ExpeditionSession 加一条日志的扩展函数 (语法糖, 让 s = s.appendLog("...") 比 s = s.copy(combatLog = ...) 短). */
    private fun ExpeditionSession.appendLog(msg: String) = copy(combatLog = combatLog + msg)
}
