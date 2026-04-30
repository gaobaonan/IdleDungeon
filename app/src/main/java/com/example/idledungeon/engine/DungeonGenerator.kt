package com.example.idledungeon.engine

import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.model.*
import kotlin.math.abs
import kotlin.random.Random

/**
 * 地牢楼层生成器 — 有机生长法 v3
 *
 * 从入口向外生长，每次从已有节点延伸走廊+房间:
 * - 直线延伸 (40%): 一条直走廊 + 尽头房间
 * - L形弯路 (60%): 走廊拐弯一次 + 尽头房间，拐角记入备选出发点
 * - 出发点: 房间(世代加权，强烈偏向最前沿) + 拐角(低权重)
 * - 方向: 密度感知，优先向空旷区域挖掘
 * - 邻接保护: 走廊每格都检查两侧，杜绝平行走廊
 */
object DungeonGenerator {

    private const val DEFAULT_WIDTH = 45
    private const val DEFAULT_HEIGHT = 32
    private const val MARGIN = 2
    private const val TARGET_ROOMS = 45
    private const val MAX_ATTEMPTS = 300
    private const val W_STRAIGHT = 40
    private const val W_L_SHAPED = 60

    private data class RoomNode(val x: Int, val y: Int, val gen: Int)
    private data class Corner(val x: Int, val y: Int)

    fun generate(
        layer: Int,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        entryX: Int = width / 2,
        entryY: Int = height - 3
    ): DungeonFloor {
        val grid = Array(height) { Array(width) { DungeonNode(NodeType.WALL) } }
        val rooms = mutableListOf<RoomNode>()
        val corners = mutableListOf<Corner>()

        // ── 入口 ──
        val ex = entryX.coerceIn(MARGIN + 2, width - MARGIN - 3)
        val ey = entryY.coerceIn(MARGIN + 2, height - MARGIN - 3)
        grid[ey][ex] = DungeonNode(NodeType.CORRIDOR, label = "0")
        rooms.add(RoomNode(ex, ey, 0))

        // ── 生长循环 ──
        var attempts = 0
        while (rooms.size < TARGET_ROOMS && attempts < MAX_ATTEMPTS) {
            attempts++
            val (sx, sy) = pickSource(rooms, corners)

            if (Random.nextInt(W_STRAIGHT + W_L_SHAPED) < W_STRAIGHT) {
                tryStraight(grid, sx, sy, rooms, width, height)
            } else {
                tryLShaped(grid, sx, sy, rooms, corners, width, height)
            }
        }

        // ── 特殊房间 + 事件 ──
        placeSpecialRooms(grid, rooms, layer)
        val upIdx = 0
        val downIdx = rooms.indices.filter { it != 0 }
            .maxByOrNull { rooms[it].gen * 3 + manhattan(rooms[it], rooms[0]) } ?: 0
        val bossIdx = rooms.indices
            .filter { it != 0 && it != downIdx }
            .sortedByDescending { rooms[it].gen }
            .take(5)
            .minByOrNull { manhattan(rooms[it], rooms[downIdx]) }
        assignEvents(grid, layer, rooms, setOfNotNull(upIdx, downIdx, bossIdx))

        return DungeonFloor(layer, width, height, grid.map { it.toList() })
    }

    // ═══════════════════════════════════════════
    // 出发点选择 — 强前沿偏向
    // ═══════════════════════════════════════════

    private fun pickSource(rooms: List<RoomNode>, corners: List<Corner>): Pair<Int, Int> {
        // 房间权重 = gen*2 + 1 (最新的房间约为入口权重的 40 倍)
        // 拐角权重 = 1 (偶尔从弯路处分支)
        val roomWeights = rooms.sumOf { it.gen * 2 + 1 }
        val total = roomWeights + corners.size
        var roll = Random.nextInt(total.coerceAtLeast(1))

        for (room in rooms) {
            roll -= (room.gen * 2 + 1)
            if (roll < 0) return room.x to room.y
        }
        for (corner in corners) {
            roll--
            if (roll < 0) return corner.x to corner.y
        }
        return rooms.last().let { it.x to it.y }
    }

    // ═══════════════════════════════════════════
    // 方向排序 — 密度感知
    // ═══════════════════════════════════════════

    private fun rankDirections(
        grid: Array<Array<DungeonNode>>, sx: Int, sy: Int, w: Int, h: Int
    ): List<Pair<Int, Int>> {
        return listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
            .sortedBy { (dx, dy) -> scoreDensity(grid, sx, sy, dx, dy, w, h) + Random.nextInt(3) }
    }

    private fun scoreDensity(
        grid: Array<Array<DungeonNode>>, sx: Int, sy: Int,
        dx: Int, dy: Int, w: Int, h: Int
    ): Int {
        var count = 0
        for (i in 1..10) {
            for (j in -2..2) {
                val nx = sx + dx * i - dy * j
                val ny = sy + dy * i + dx * j
                if (ny in 0 until h && nx in 0 until w && grid[ny][nx].type != NodeType.WALL) count++
            }
        }
        return count
    }

    // ═══════════════════════════════════════════
    // 直线延伸
    // ═══════════════════════════════════════════

    private fun tryStraight(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int,
        rooms: MutableList<RoomNode>,
        w: Int, h: Int
    ): Boolean {
        for ((dx, dy) in rankDirections(grid, sx, sy, w, h)) {
            val len = Random.nextInt(3, 7)
            if (!canExtendStraight(grid, sx, sy, dx, dy, len, w, h)) continue
            for (i in 1 until len) grid[sy + dy * i][sx + dx * i] = DungeonNode(NodeType.CORRIDOR)
            val rx = sx + dx * len; val ry = sy + dy * len
            val roomGen = rooms.size
            grid[ry][rx] = DungeonNode(NodeType.CORRIDOR, label = roomGen.toString())
            rooms.add(RoomNode(rx, ry, roomGen))
            return true
        }
        return false
    }

    // ═══════════════════════════════════════════
    // L形延伸
    // ═══════════════════════════════════════════

    private fun tryLShaped(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int,
        rooms: MutableList<RoomNode>,
        corners: MutableList<Corner>,
        w: Int, h: Int
    ): Boolean {
        for ((dx1, dy1) in rankDirections(grid, sx, sy, w, h)) {
            val len1 = Random.nextInt(2, 5)
            for ((dx2, dy2) in listOf(dy1 to -dx1, -dy1 to dx1).shuffled()) {
                val len2 = Random.nextInt(2, 5)
                if (!canExtendL(grid, sx, sy, dx1, dy1, len1, dx2, dy2, len2, w, h)) continue

                // 刻第一段
                for (i in 1..len1) grid[sy + dy1 * i][sx + dx1 * i] = DungeonNode(NodeType.CORRIDOR)
                val tx = sx + dx1 * len1; val ty = sy + dy1 * len1
                // 刻第二段
                for (i in 1 until len2) grid[ty + dy2 * i][tx + dx2 * i] = DungeonNode(NodeType.CORRIDOR)
                val rx = tx + dx2 * len2; val ry = ty + dy2 * len2
                val roomGen = rooms.size
                grid[ry][rx] = DungeonNode(NodeType.CORRIDOR, label = roomGen.toString())

                corners.add(Corner(tx, ty))
                rooms.add(RoomNode(rx, ry, roomGen))
                return true
            }
        }
        return false
    }

    // ═══════════════════════════════════════════
    // 延伸校验 — 严格邻接保护
    // ═══════════════════════════════════════════

    /**
     * 直线校验: 除第一格(紧邻出发点)外，每格两侧都必须是墙。
     */
    private fun canExtendStraight(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int, dx: Int, dy: Int, len: Int, w: Int, h: Int
    ): Boolean {
        for (i in 1..len) {
            val nx = sx + dx * i; val ny = sy + dy * i
            if (!inBounds(nx, ny, w, h)) return false
            if (grid[ny][nx].type != NodeType.WALL) return false
            if (i > 1 && hasPerpNeighbor(grid, nx, ny, dx, dy, w, h)) return false
        }
        return hasRoomClearance(grid, sx + dx * len, sy + dy * len, dx, dy, w, h)
    }

    /**
     * L形校验:
     * - 第一段: 除第一格外每格检查侧面 (含拐角格)
     * - 第二段: 每格都检查侧面 (含紧邻拐角的第一格)
     * - 终点房间: 8邻居间距
     */
    private fun canExtendL(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int,
        dx1: Int, dy1: Int, len1: Int,
        dx2: Int, dy2: Int, len2: Int,
        w: Int, h: Int
    ): Boolean {
        // 第一段 (含拐角, 除第一格)
        for (i in 1..len1) {
            val nx = sx + dx1 * i; val ny = sy + dy1 * i
            if (!inBounds(nx, ny, w, h)) return false
            if (grid[ny][nx].type != NodeType.WALL) return false
            if (i > 1 && hasPerpNeighbor(grid, nx, ny, dx1, dy1, w, h)) return false
        }

        // 第二段 (含紧邻拐角的第一格, 全部检查)
        val tx = sx + dx1 * len1; val ty = sy + dy1 * len1
        for (i in 1..len2) {
            val nx = tx + dx2 * i; val ny = ty + dy2 * i
            if (!inBounds(nx, ny, w, h)) return false
            if (grid[ny][nx].type != NodeType.WALL) return false
            if (hasPerpNeighbor(grid, nx, ny, dx2, dy2, w, h)) return false
        }

        return hasRoomClearance(grid, tx + dx2 * len2, ty + dy2 * len2, dx2, dy2, w, h)
    }

    private fun hasPerpNeighbor(
        grid: Array<Array<DungeonNode>>,
        nx: Int, ny: Int, dx: Int, dy: Int, w: Int, h: Int
    ): Boolean {
        // 垂直于行进方向的两个邻居
        val p1x = nx - dy; val p1y = ny + dx
        val p2x = nx + dy; val p2y = ny - dx
        if (p1y in 0 until h && p1x in 0 until w && grid[p1y][p1x].type != NodeType.WALL) return true
        if (p2y in 0 until h && p2x in 0 until w && grid[p2y][p2x].type != NodeType.WALL) return true
        return false
    }

    /** 房间周围 5×5 范围 (除来路走廊方向) 必须全是墙，防止房间靠太近 */
    private fun hasRoomClearance(
        grid: Array<Array<DungeonNode>>,
        rx: Int, ry: Int, fromDx: Int, fromDy: Int, w: Int, h: Int
    ): Boolean {
        for (cy in ry - 2..ry + 2) {
            for (cx in rx - 2..rx + 2) {
                if (cx == rx && cy == ry) continue
                // 来路走廊方向排除 2 格深
                val relX = cx - rx; val relY = cy - ry
                if (relX == -fromDx && relY == -fromDy) continue
                if (relX == -fromDx * 2 && relY == -fromDy * 2) continue
                if (cy !in 0 until h || cx !in 0 until w) continue
                if (grid[cy][cx].type != NodeType.WALL) return false
            }
        }
        return true
    }

    private fun inBounds(x: Int, y: Int, w: Int, h: Int): Boolean =
        x in MARGIN + 1 until w - MARGIN - 1 && y in MARGIN + 1 until h - MARGIN - 1

    // ═══════════════════════════════════════════
    // 特殊房间
    // ═══════════════════════════════════════════

    private fun placeSpecialRooms(grid: Array<Array<DungeonNode>>, rooms: List<RoomNode>, layer: Int) {
        if (rooms.size < 2) return
        val upIdx = 0
        val downIdx = rooms.indices.filter { it != 0 }
            .maxByOrNull { rooms[it].gen * 3 + manhattan(rooms[it], rooms[0]) } ?: return
        val bossIdx = rooms.indices
            .filter { it != 0 && it != downIdx }
            .sortedByDescending { rooms[it].gen }
            .take(5)
            .minByOrNull { manhattan(rooms[it], rooms[downIdx]) }

        grid[rooms[upIdx].y][rooms[upIdx].x] = DungeonNode(NodeType.STAIRS, NodeEvent.STAIRS_UP)
        grid[rooms[downIdx].y][rooms[downIdx].x] = DungeonNode(NodeType.STAIRS, NodeEvent.STAIRS_DOWN)
        if (bossIdx != null) {
            // 楼层 boss 等级 = 楼层 × 3 + 1; 没设计的层级则跳过
            val bossLevel = layer * 3 + 1
            val boss = MonsterRegistry.bossForLevel(bossLevel)
            if (boss != null) {
                grid[rooms[bossIdx].y][rooms[bossIdx].x] = DungeonNode(
                    NodeType.BOSS_ROOM, NodeEvent.BOSS_FIGHT, monsterIds = listOf(boss.id)
                )
            }
        }
    }

    // ═══════════════════════════════════════════
    // 事件分配
    // ═══════════════════════════════════════════

    private fun assignEvents(
        grid: Array<Array<DungeonNode>>, layer: Int,
        rooms: List<RoomNode>, specials: Set<Int>
    ) {
        for (i in rooms.indices) {
            if (i in specials) continue
            val r = rooms[i]; val cell = grid[r.y][r.x]
            if (cell.type == NodeType.WALL) continue
            // 房间分布: 50% 战斗, 20% 守护宝藏, 20% 陷阱, 10% 解谜
            grid[r.y][r.x] = when (Random.nextInt(100)) {
                in 0..49  -> randomEncounterCell(cell, layer)
                in 50..69 -> randomGuardedLootCell(cell, layer)
                in 70..89 -> cell.copy(event = NodeEvent.TRAP)
                else      -> randomPuzzleCell(cell)
            }
        }
        // 走廊不再生成事件
    }

    /** 随机生成一个 1-4 只怪的遭遇房间 (本层怪物等级 = 楼层×2 ±1) */
    private fun randomEncounterCell(cell: DungeonNode, layer: Int): DungeonNode {
        val pool = monsterPoolForLayer(layer)
        if (pool.isEmpty()) return cell.copy(event = NodeEvent.TRAP)  // 兜底
        val count = Random.nextInt(1, 5)  // 1..4
        val ids = List(count) { pool.random().id }
        return cell.copy(event = NodeEvent.ENCOUNTER, monsterIds = ids)
    }

    /** 楼层对应的普通怪物池: 等级 = 楼层×2 ±1 (Floor 1: L1-3, Floor 2: L3-5, ...) */
    private fun monsterPoolForLayer(layer: Int): List<Monster> {
        val minL = (layer * 2 - 1).coerceAtLeast(1)
        val maxL = layer * 2 + 1
        return MonsterRegistry.byLevelRange(minL, maxL)
    }

    /** 解谜房间: INT 破解, 成功获得宝物 */
    private fun randomPuzzleCell(cell: DungeonNode): DungeonNode {
        val loot = mapOf(
            "magic_crystal" to 70, "ancient_coin" to 50, "spider_silk" to 30,
            "dark_shard" to 25, "minotaur_horn" to 12, "lich_phylactery" to 5
        )
        return cell.copy(event = NodeEvent.PUZZLE, lootTable = loot)
    }

    /** 守护宝藏: 1-4 只怪 + 战利品 (战胜后才开宝箱) */
    private fun randomGuardedLootCell(cell: DungeonNode, layer: Int): DungeonNode {
        val pool = monsterPoolForLayer(layer)
        val loot = mutableMapOf(
            "iron_ore" to 70, "monster_bone" to 50, "magic_crystal" to 30,
            "ancient_coin" to 25, "spider_silk" to 20, "dark_shard" to 12
        )
        // 主线任务奖励: 第二层守卫宝箱 30% 几率掉"苔纹剑" (5 次没出有保底, 详见 ExplorationEngine)
        if (layer == 2) loot[QUEST_EPIC_SWORD_ID] = 30
        if (pool.isEmpty()) return cell.copy(event = NodeEvent.LOOT, lootTable = loot)
        val count = Random.nextInt(1, 5)
        val ids = List(count) { pool.random().id }
        return cell.copy(event = NodeEvent.GUARDED_LOOT, monsterIds = ids, lootTable = loot)
    }

    private fun manhattan(a: RoomNode, b: RoomNode): Int = abs(a.x - b.x) + abs(a.y - b.y)
}
