package com.example.idledungeon.engine

import com.example.idledungeon.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DungeonGenerator 单元测试.
 *
 * 测试策略:
 *   - DungeonGenerator 跟 CombatEngine 一样, 直接调用 kotlin.random.Random.Default — 没法注入种子.
 *     ⇒ 所有测试只走两种风格:
 *       ① 不变量 (invariant): 不管随机怎么跑都必须成立 (e.g. 恰好 1 个上楼梯, 上下楼连通)
 *       ② 统计 (statistical): 跑 N 次后平均值/出现率应接近设计 (e.g. 30 次平均房间数 ≥ 35)
 *   - 不 mock 任何依赖. MonsterRegistry / QUEST_EPIC_SWORD_ID 都用真实值.
 *
 * 关键参数 (来自 DungeonGenerator 私有常量):
 *   DEFAULT_WIDTH = 45, DEFAULT_HEIGHT = 32, MARGIN = 2, TARGET_ROOMS = 45
 *   inBounds 范围: x ∈ [3, w-4], y ∈ [3, h-4]  (即 MARGIN+1 .. w-MARGIN-2)
 *   入口 clamp 范围: x ∈ [4, w-5], y ∈ [4, h-5]  (即 MARGIN+2 .. w-MARGIN-3)
 *
 * Boss 等级 = layer × 3 + 1, MonsterRegistry 配置了 lv 4/7/10/13/16 — 即 layer 1-5 都有 Boss.
 */
class DungeonGeneratorTest {

    // ─────────────────────────────────────────────────────────
    //  辅助函数
    // ─────────────────────────────────────────────────────────

    /** 简写: 默认参数生成 layer 1 的地图. */
    private fun gen(): DungeonFloor = DungeonGenerator.generate(layer = 1)

    /** 找所有满足 predicate 的格子坐标. */
    private fun cellsWhere(f: DungeonFloor, p: (DungeonNode) -> Boolean): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until f.height) for (x in 0 until f.width)
            if (p(f.grid[y][x])) out.add(x to y)
        return out
    }

    /** 找上楼梯坐标 (合法地图恰有 1 个). */
    private fun stairsUp(f: DungeonFloor): Pair<Int, Int> =
        cellsWhere(f) { it.event == NodeEvent.STAIRS_UP }.single()

    /**
     * 从 (sx, sy) 出发, 4 方向 BFS, 只走非墙格, 返回所有可达坐标集合.
     * 用于验证: 上下楼连通 / 无孤岛 / 事件房可达.
     */
    private fun reachableFrom(f: DungeonFloor, sx: Int, sy: Int): Set<Pair<Int, Int>> {
        val seen = mutableSetOf(sx to sy)
        val q = ArrayDeque<Pair<Int, Int>>().apply { add(sx to sy) }
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (q.isNotEmpty()) {
            val (x, y) = q.removeFirst()
            for ((dx, dy) in dirs) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until f.width || ny !in 0 until f.height) continue
                if (f.grid[ny][nx].type == NodeType.WALL) continue
                if ((nx to ny) in seen) continue
                seen.add(nx to ny); q.add(nx to ny)
            }
        }
        return seen
    }

    /** 所有非墙格坐标. */
    private fun allWalkable(f: DungeonFloor): Set<Pair<Int, Int>> =
        cellsWhere(f) { it.type != NodeType.WALL }.toSet()

    // =========================================================
    //  1. 输出形状 (sanity)
    // =========================================================

    @Test
    fun `输出 layer 等于输入 layer`() {
        listOf(1, 2, 3, 5).forEach { layer ->
            val f = DungeonGenerator.generate(layer = layer)
            assertEquals(layer, f.layer)
        }
    }

    @Test
    fun `输出 width height 等于输入`() {
        val f = DungeonGenerator.generate(layer = 1, width = 30, height = 25)
        assertEquals(30, f.width)
        assertEquals(25, f.height)
    }

    @Test
    fun `grid 行数等于 height`() {
        val f = DungeonGenerator.generate(layer = 1, width = 30, height = 25)
        assertEquals(25, f.grid.size)
    }

    @Test
    fun `grid 每行列数等于 width`() {
        val f = gen()
        assertTrue(f.grid.all { it.size == f.width })
    }

    // =========================================================
    //  2. 特殊房间 (入口 / 出口 / Boss)
    // =========================================================

    @Test
    fun `恰好一个上楼梯 STAIRS_UP`() {
        val f = gen()
        assertEquals(1, cellsWhere(f) { it.event == NodeEvent.STAIRS_UP }.size)
    }

    @Test
    fun `恰好一个下楼梯 STAIRS_DOWN`() {
        val f = gen()
        assertEquals(1, cellsWhere(f) { it.event == NodeEvent.STAIRS_DOWN }.size)
    }

    @Test
    fun `上下楼位置不同`() {
        val f = gen()
        val up = stairsUp(f)
        val down = cellsWhere(f) { it.event == NodeEvent.STAIRS_DOWN }.single()
        assertNotEquals(up, down)
    }

    @Test
    fun `默认 45x32 + layer1 - 恰好一个 Boss 房`() {
        // 45 个房间足够分出 boss; layer 1 → bossLevel 4 → boss_rat_king 存在
        val f = gen()
        assertEquals(1, cellsWhere(f) { it.event == NodeEvent.BOSS_FIGHT }.size)
    }

    @Test
    fun `Boss 房 type 为 BOSS_ROOM 且自带 1 个怪物 ID`() {
        val f = gen()
        val (bx, by) = cellsWhere(f) { it.event == NodeEvent.BOSS_FIGHT }.single()
        val node = f.grid[by][bx]
        assertEquals(NodeType.BOSS_ROOM, node.type)
        assertEquals(1, node.monsterIds.size)
        assertTrue("Boss 怪物 ID 应以 boss_ 开头", node.monsterIds[0].startsWith("boss_"))
    }

    @Test
    fun `上楼梯所在格 type 为 STAIRS`() {
        val f = gen()
        val (ux, uy) = stairsUp(f)
        assertEquals(NodeType.STAIRS, f.grid[uy][ux].type)
    }

    @Test
    fun `下楼梯所在格 type 为 STAIRS`() {
        val f = gen()
        val (dx, dy) = cellsWhere(f) { it.event == NodeEvent.STAIRS_DOWN }.single()
        assertEquals(NodeType.STAIRS, f.grid[dy][dx].type)
    }

    // =========================================================
    //  3. 连通性 (核心 — 防止地图断开/孤岛)
    // =========================================================

    @Test
    fun `从上楼梯出发 - 可达下楼梯`() {
        val f = gen()
        val (ux, uy) = stairsUp(f)
        val down = cellsWhere(f) { it.event == NodeEvent.STAIRS_DOWN }.single()
        val reach = reachableFrom(f, ux, uy)
        assertTrue("下楼梯 $down 不在 ${reach.size} 个可达格里 — 地图断开!", down in reach)
    }

    @Test
    fun `从上楼梯出发 - 可达 Boss 房`() {
        val f = gen()
        val (ux, uy) = stairsUp(f)
        val boss = cellsWhere(f) { it.event == NodeEvent.BOSS_FIGHT }.singleOrNull() ?: return
        assertTrue("Boss 房 $boss 不可达", boss in reachableFrom(f, ux, uy))
    }

    @Test
    fun `所有非墙格都从入口可达 (无孤岛)`() {
        // 最关键的不变量: 玩家可以走到地图上每一个非墙格
        val f = gen()
        val (ux, uy) = stairsUp(f)
        val walkable = allWalkable(f)
        val reach = reachableFrom(f, ux, uy)
        assertEquals(
            "存在 ${walkable.size - reach.size} 个孤立非墙格 — 玩家走不到",
            walkable, reach
        )
    }

    @Test
    fun `所有事件房间都从入口可达`() {
        val f = gen()
        val (ux, uy) = stairsUp(f)
        val reach = reachableFrom(f, ux, uy)
        val events = listOf(
            NodeEvent.TRAP, NodeEvent.ENCOUNTER,
            NodeEvent.GUARDED_LOOT, NodeEvent.PUZZLE
        )
        cellsWhere(f) { it.event in events }.forEach { pos ->
            assertTrue("事件房 $pos 不可达", pos in reach)
        }
    }

    // =========================================================
    //  4. 边界 — margin 不能被穿透
    // =========================================================

    @Test
    fun `所有非墙格都在 inBounds 范围内 (margin 留出)`() {
        // inBounds: x ∈ [3, w-4], y ∈ [3, h-4]
        val f = gen()
        allWalkable(f).forEach { (x, y) ->
            assertTrue("($x,$y) 超出 inBounds x 范围 [3, ${f.width - 4}]",
                x in 3..(f.width - 4))
            assertTrue("($x,$y) 超出 inBounds y 范围 [3, ${f.height - 4}]",
                y in 3..(f.height - 4))
        }
    }

    // =========================================================
    //  5. 入口 clamp
    // =========================================================

    @Test
    fun `入口坐标越界负值 - clamp 到合法范围`() {
        val f = DungeonGenerator.generate(layer = 1, entryX = -100, entryY = -100)
        val (ux, uy) = stairsUp(f)
        assertTrue("入口 x=$ux 应被 clamp 到 [4, ${f.width - 5}]", ux in 4..(f.width - 5))
        assertTrue("入口 y=$uy 应被 clamp 到 [4, ${f.height - 5}]", uy in 4..(f.height - 5))
    }

    @Test
    fun `入口坐标超大值 - clamp 到合法范围`() {
        val f = DungeonGenerator.generate(layer = 1, entryX = 9999, entryY = 9999)
        val (ux, uy) = stairsUp(f)
        assertTrue(ux in 4..(f.width - 5))
        assertTrue(uy in 4..(f.height - 5))
    }

    @Test
    fun `合法入口坐标 - clamp 后等于自身`() {
        // (22, 20) 都在合法 clamp 范围 [4, 40] / [4, 27] 内 → 应是恒等
        val f = DungeonGenerator.generate(layer = 1, entryX = 22, entryY = 20)
        val (ux, uy) = stairsUp(f)
        assertEquals(22, ux)
        assertEquals(20, uy)
    }

    // =========================================================
    //  6. 楼层差异
    // =========================================================

    @Test
    fun `layer 1 到 5 都能生成 + layer 字段正确`() {
        (1..5).forEach { layer ->
            val f = DungeonGenerator.generate(layer = layer)
            assertEquals(layer, f.layer)
            assertEquals("layer=$layer 应有上楼梯",
                1, cellsWhere(f) { it.event == NodeEvent.STAIRS_UP }.size)
            assertEquals("layer=$layer 应有下楼梯",
                1, cellsWhere(f) { it.event == NodeEvent.STAIRS_DOWN }.size)
        }
    }

    // =========================================================
    //  7. 事件房间内容一致性
    // =========================================================

    @Test
    fun `所有遭遇战房间带 1-4 个怪物 ID`() {
        val f = gen()
        cellsWhere(f) { it.event == NodeEvent.ENCOUNTER }.forEach { (x, y) ->
            val ids = f.grid[y][x].monsterIds
            assertTrue("遭遇战 ($x,$y) 没怪", ids.isNotEmpty())
            assertTrue("怪物数 ${ids.size} 应 ∈ [1,4]", ids.size in 1..4)
        }
    }

    @Test
    fun `所有守护宝藏房带怪物 + 战利品表`() {
        val f = gen()
        cellsWhere(f) { it.event == NodeEvent.GUARDED_LOOT }.forEach { (x, y) ->
            val node = f.grid[y][x]
            assertTrue("守护宝藏 ($x,$y) 没怪", node.monsterIds.isNotEmpty())
            assertTrue("守护宝藏 ($x,$y) 没战利品", node.lootTable.isNotEmpty())
        }
    }

    @Test
    fun `所有解谜房带战利品表`() {
        val f = gen()
        cellsWhere(f) { it.event == NodeEvent.PUZZLE }.forEach { (x, y) ->
            assertTrue("解谜 ($x,$y) 没战利品", f.grid[y][x].lootTable.isNotEmpty())
        }
    }

    @Test
    fun `F2 守护宝藏战利品表里会出现主线剑 (20 次内能看到)`() {
        // DungeonGenerator: if (layer == 2) loot[QUEST_EPIC_SWORD_ID] = 30
        // 单次不一定每个守护宝藏都有, 但 20 次内至少出现一次
        val sawEpicSword = (1..20).any {
            val f = DungeonGenerator.generate(layer = 2)
            cellsWhere(f) { it.event == NodeEvent.GUARDED_LOOT }
                .any { (x, y) -> QUEST_EPIC_SWORD_ID in f.grid[y][x].lootTable.keys }
        }
        assertTrue("20 次 F2 生成都没在守护宝藏里看到主线剑 — 概率几乎为 0, 应该是 bug", sawEpicSword)
    }

    // =========================================================
    //  8. 边缘 / 鲁棒性
    // =========================================================

    @Test
    fun `小尺寸 20x20 - 不崩, 维度正确`() {
        val f = DungeonGenerator.generate(layer = 1, width = 20, height = 20)
        assertEquals(20, f.width)
        assertEquals(20, f.height)
        // 小图可能没法放下 boss, 但至少应有 1 个上楼梯
        assertEquals(1, cellsWhere(f) { it.event == NodeEvent.STAIRS_UP }.size)
    }

    @Test
    fun `20 次连续生成 - 都不抛异常`() {
        repeat(20) { DungeonGenerator.generate(layer = 1) }   // 跑完即视为通过
    }

    // =========================================================
    //  9. 统计性质 (跨多次运行)
    // =========================================================

    @Test
    fun `30 次生成 - 平均房间数应该接近 TARGET_ROOMS 45`() {
        // generator 不保证总达到 TARGET_ROOMS (MAX_ATTEMPTS 兜底), 但应该大部分接近
        // 房间 = 楼梯 + Boss + 带 roomNumber 的格子
        val counts = (1..30).map { _ ->
            val f = DungeonGenerator.generate(layer = 1)
            cellsWhere(f) {
                it.roomNumber != null || it.type == NodeType.BOSS_ROOM || it.type == NodeType.STAIRS
            }.size
        }
        val avg = counts.average()
        assertTrue("平均房间数 $avg 偏少, 期望接近 45", avg >= 35.0)
    }

    @Test
    fun `30 次生成 - 4 种随机事件 + 楼梯 + boss 全部至少出现一次`() {
        val seen = mutableSetOf<NodeEvent>()
        repeat(30) {
            val f = DungeonGenerator.generate(layer = 1)
            f.grid.flatten().forEach { seen.add(it.event) }
        }
        listOf(
            NodeEvent.TRAP, NodeEvent.ENCOUNTER, NodeEvent.GUARDED_LOOT, NodeEvent.PUZZLE,
            NodeEvent.STAIRS_UP, NodeEvent.STAIRS_DOWN, NodeEvent.BOSS_FIGHT
        ).forEach { ev ->
            assertTrue("事件 $ev 30 次都没出现 — 概率几乎为 0, 八成有 bug", ev in seen)
        }
    }

    @Test
    fun `30 次生成 - 战斗类房间应该多于非战斗类 (设计 70 vs 30)`() {
        // 设计目标: 50% ENCOUNTER + 20% GUARDED_LOOT = 70% 战斗类; 20% TRAP + 10% PUZZLE = 30% 非战斗
        var combat = 0; var nonCombat = 0
        repeat(30) {
            val f = DungeonGenerator.generate(layer = 1)
            f.grid.flatten().forEach {
                when (it.event) {
                    NodeEvent.ENCOUNTER, NodeEvent.GUARDED_LOOT -> combat++
                    NodeEvent.TRAP, NodeEvent.PUZZLE -> nonCombat++
                    else -> {}
                }
            }
        }
        assertTrue("战斗类 $combat 应明显多于非战斗类 $nonCombat", combat > nonCombat)
    }

    @Test
    fun `两次连续生成应该不一致 (没 seed 注入 — 必有差异)`() {
        // 文档化设计意图: 当前没有 seed 注入, Random.Default 每次结果都不同
        val f1 = DungeonGenerator.generate(layer = 1)
        val f2 = DungeonGenerator.generate(layer = 1)
        val identical = f1.grid.indices.all { y ->
            f1.grid[y].indices.all { x ->
                f1.grid[y][x].type == f2.grid[y][x].type &&
                    f1.grid[y][x].event == f2.grid[y][x].event
            }
        }
        assertFalse("两次生成的地图完全一致 — 是不是某处把 Random 写死了?", identical)
    }

    // =========================================================
    //  10. 不变量回归 (易破坏 / 非显性)
    // =========================================================

    @Test
    fun `走廊过道格 (roomNumber=null) 不应该带事件`() {
        // 设计约束: 事件只挂在房间格上 (有 roomNumber 或是 Boss / 楼梯)
        // 走廊纯过道格 (CORRIDOR + roomNumber=null) 必须 event=NONE
        val f = gen()
        cellsWhere(f) { it.type == NodeType.CORRIDOR && it.roomNumber == null }
            .forEach { (x, y) ->
                assertEquals("过道格 ($x,$y) 不应有事件",
                    NodeEvent.NONE, f.grid[y][x].event)
            }
    }

    @Test
    fun `房间编号在 grid 中应该唯一 (不会两个格子标同一个 roomNumber)`() {
        val f = gen()
        val numbers = cellsWhere(f) { it.roomNumber != null }
            .map { (x, y) -> f.grid[y][x].roomNumber!! }
        assertEquals("发现重复 roomNumber: ${numbers.groupingBy { it }.eachCount().filter { it.value > 1 }}",
            numbers.size, numbers.toSet().size)
    }
}
