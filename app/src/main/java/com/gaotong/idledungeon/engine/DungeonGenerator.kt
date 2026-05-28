package com.gaotong.idledungeon.engine

import com.gaotong.idledungeon.data.MonsterRegistry
import com.gaotong.idledungeon.model.*
import kotlin.math.abs
import kotlin.random.Random

/**
 * 地牢楼层生成器: 有机生长算法.
 *
 * 思路是"从入口往外发芽": 每次从已有房间挑出发点, 朝某方向挖走廊 + 末端放房间;
 * 形态二选一: 直走廊 (40%) 或 L 形拐弯 (60%, 拐角入备选出发点). 重复直到房间够 TARGET_ROOMS
 * 或尝试次数耗尽. 关键技巧: 出发点加权偏向最新生成的房间避免挤团, 朝空旷方向走 (密度感知),
 * 走廊邻接保护防贴边, 房间间距 5×5. 生完后放上下楼梯 + Boss 房, 剩余房间随机刷事件.
 *
 * 没有 seed 注入, 每次调用生成的地牢都不一样.
 */
object DungeonGenerator {

    // 网格 & 生长参数
    private const val DEFAULT_WIDTH = 45        // 默认地图宽度
    private const val DEFAULT_HEIGHT = 32       // 默认地图高度
    private const val MARGIN = 2                // 距边界的安全距离 (房间不能贴边)
    private const val TARGET_ROOMS = 45         // 目标房间数 (生长成功就停)
    private const val MAX_ATTEMPTS = 300        // 失败兜底 — 防卡死

    // 形态权重
    private const val W_STRAIGHT = 40           // 直线延伸权重 → 40/(40+60) = 40%
    private const val W_L_SHAPED = 60           // L 形权重 → 60%

    /** 一个房间节点. gen = 第几代 (入口=0, 第一个生出来的=1, 越大越前沿) */
    private data class RoomNode(val x: Int, val y: Int, val gen: Int)

    /** L 形走廊的拐角格 — 作为低权重的备选出发点, 给地图加点分支感 */
    private data class Corner(val x: Int, val y: Int)

    /**
     * 主入口 — 生成一层完整地牢.
     *
     * 流程:
     *   1. 全图初始化为墙
     *   2. 放置入口节点
     *   3. 主循环: 直到房间够 / 尝试次数耗尽 — 选出发点 → 选形态 → 延伸
     *   4. 标记特殊房间 (上楼/下楼/Boss)
     *   5. 给剩余房间随机刷事件
     */
    fun generate(
        layer: Int,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        entryX: Int = width / 2,
        entryY: Int = height - 3
    ): DungeonFloor {
        val grid = Array(height) { Array(width) { DungeonNode(NodeType.WALL) } }        //二维数组666，elem是房间节点
        val rooms = mutableListOf<RoomNode>()
        val corners = mutableListOf<Corner>()

        // 入口 (强制夹在安全范围内)
        val ex = entryX.coerceIn(MARGIN + 2, width - MARGIN - 3)            //maxmin的kotlin函数
        val ey = entryY.coerceIn(MARGIN + 2, height - MARGIN - 3)
        grid[ey][ex] = DungeonNode(NodeType.CORRIDOR, roomNumber = 0)
        rooms.add(RoomNode(ex, ey, 0))

        // 生长循环 — 房间够了或尝试太多就停
        var attempts = 0
        while (rooms.size < TARGET_ROOMS && attempts < MAX_ATTEMPTS) {
            attempts++
            val (sx, sy) = pickSource(rooms, corners)

            // 按权重决定本次延伸的形态
            if (Random.nextInt(W_STRAIGHT + W_L_SHAPED) < W_STRAIGHT) {     //直线还是L的权重计算，！40！刚刚好，再低会有bug
                tryStraight(grid, sx, sy, rooms, width, height)
            } else {
                tryLShaped(grid, sx, sy, rooms, corners, width, height)
            }
        }

        // 特殊房间 (楼梯 + Boss)
        placeSpecialRooms(grid, rooms, layer)
        // 拿到同一组特殊房间索引, 告诉 assignEvents 不要在这些房间上刷随机事件
        val (upIdx, downIdx, bossIdx) = pickSpecialIndices(rooms) ?: Triple(0, 0, null)
        assignEvents(grid, layer, rooms, setOfNotNull(upIdx, downIdx, bossIdx))

        return DungeonFloor(layer, width, height, grid.map { it.toList() })
    }

    // 出发点选择 — 强前沿偏向

    /**
     * 加权随机挑一个 (x, y) 作为下次延伸的出发点.
     * 房间权重 = gen×2 + 1 (越新权重越大, 入口=1, 第 20 代=41)
     * 拐角权重 = 1 (偶尔从弯路分支, 让地图不那么"主干道")
     */
    private fun pickSource(rooms: List<RoomNode>, corners: List<Corner>): Pair<Int, Int> {
        val roomWeights = rooms.sumOf { it.gen * 2 + 1 }            //权重：gen越高，最后可能性越大
        val total = roomWeights + corners.size                      //走廊也算，但概率很小
        var roll = Random.nextInt(total.coerceAtLeast(1))

        // 经典轮盘赌: roll 从总权重里抽一个数, 逐项减权重, 减到负数那一项中奖
        for (room in rooms) {
            roll -= (room.gen * 2 + 1)          //权重应用
            if (roll < 0) return room.x to room.y
        }
        for (corner in corners) {
            roll--
            if (roll < 0) return corner.x to corner.y
        }
        return rooms.last().let { it.x to it.y }   // 兜底 — 浮点累加误差导致全走完时用最后一个
    }

    // 方向排序 — 密度感知

    /**
     * 把 4 个方向 (上下左右) 按 "前方密度" 升序排列, 越空旷越靠前.
     * 加一点随机扰动 (Random.nextInt(3)) 防止永远走同一方向.
     */
    private fun rankDirections(
        grid: Array<Array<DungeonNode>>, sx: Int, sy: Int, w: Int, h: Int
    ): List<Pair<Int, Int>> {
        return listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
            .sortedBy { (dx, dy) -> scoreDensity(grid, sx, sy, dx, dy, w, h) + Random.nextInt(3) }      //从小到大排列计算结果
    }

    /**
     * 数指定方向前方一个 10×5 长方形里有多少"非墙"格. 数得越多, 表示越拥挤.
     * (dx, dy) = 行进方向, (-dy, dx) / (dy, -dx) = 两侧偏移.
     */
    private fun scoreDensity(
        grid: Array<Array<DungeonNode>>, sx: Int, sy: Int,
        dx: Int, dy: Int, w: Int, h: Int
    ): Int {
        var count = 0
        for (i in 1..10) {          //目前算10*5，可以更大？
            for (j in -2..2) {
                val nx = sx + dx * i - dy * j
                val ny = sy + dy * i + dx * j
                if (ny in 0 until h && nx in 0 until w && grid[ny][nx].type != NodeType.WALL) count++       //非墙格子
            }
        }
        return count
    }

    // 直线延伸

    /**
     * 试试从 (sx, sy) 直挖一条 3-6 格的走廊 + 末端房间.
     * 按方向密度排序, 第一个能成的方向就用. 全失败返回 false.
     */
    private fun tryStraight(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int,
        rooms: MutableList<RoomNode>,
        w: Int, h: Int
    ): Boolean {
        for ((dx, dy) in rankDirections(grid, sx, sy, w, h)) {
            val len = Random.nextInt(3, 7)
            if (!canExtendStraight(grid, sx, sy, dx, dy, len, w, h)) continue
            // 中间格刻成走廊, 末端格刻成"带房间编号的走廊"
            for (i in 1 until len) grid[sy + dy * i][sx + dx * i] = DungeonNode(NodeType.CORRIDOR)
            val rx = sx + dx * len; val ry = sy + dy * len
            val roomGen = rooms.size
            grid[ry][rx] = DungeonNode(NodeType.CORRIDOR, roomNumber = roomGen)
            rooms.add(RoomNode(rx, ry, roomGen))
            return true
        }
        return false
    }

    // L 形延伸

    /**
     * 试试从 (sx, sy) 挖一段 2-4 格 → 拐 90° → 再挖 2-4 格 + 末端房间.
     * 双重循环: 外层选第一段方向, 内层选拐弯方向 (左拐 / 右拐 各试一次).
     */
    private fun tryLShaped(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int,
        rooms: MutableList<RoomNode>,
        corners: MutableList<Corner>,
        w: Int, h: Int
    ): Boolean {
        for ((dx1, dy1) in rankDirections(grid, sx, sy, w, h)) {
            val len1 = Random.nextInt(2, 5)
            // (dy1, -dx1) = 左拐, (-dy1, dx1) = 右拐
            for ((dx2, dy2) in listOf(dy1 to -dx1, -dy1 to dx1).shuffled()) {
                val len2 = Random.nextInt(2, 5)
                if (!canExtendL(grid, sx, sy, dx1, dy1, len1, dx2, dy2, len2, w, h)) continue

                // 刻第一段
                for (i in 1..len1) grid[sy + dy1 * i][sx + dx1 * i] = DungeonNode(NodeType.CORRIDOR)
                val tx = sx + dx1 * len1; val ty = sy + dy1 * len1   // 拐角坐标
                // 刻第二段
                for (i in 1 until len2) grid[ty + dy2 * i][tx + dx2 * i] = DungeonNode(NodeType.CORRIDOR)
                val rx = tx + dx2 * len2; val ry = ty + dy2 * len2   // 末端房间坐标
                val roomGen = rooms.size
                grid[ry][rx] = DungeonNode(NodeType.CORRIDOR, roomNumber = roomGen)

                corners.add(Corner(tx, ty))          // 拐角记入备选出发点
                rooms.add(RoomNode(rx, ry, roomGen))
                return true
            }
        }
        return false
    }

    // 延伸校验 — 严格邻接保护

    /**
     * 直线校验: 整条走廊必须全在边界内 + 全是墙, 且除起始紧邻格外, 每格两侧都不能挨已有通道,
     * 末端房间还要满足 5×5 间距.
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
     * L 形校验:
     *   - 第一段含拐角格 — 除紧邻起点的第一格外, 都查侧面 (拐角格也算)
     *   - 第二段全部查侧面 (拐角后第一格也要查)
     *   - 末端房间 5×5 间距
     */
    private fun canExtendL(
        grid: Array<Array<DungeonNode>>,
        sx: Int, sy: Int,
        dx1: Int, dy1: Int, len1: Int,
        dx2: Int, dy2: Int, len2: Int,
        w: Int, h: Int
    ): Boolean {
        // 第一段 (含拐角, 跳过紧邻起点第一格)
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

    /**
     * 检查 (nx, ny) 这一格的垂直两侧 (相对于行进方向 dx, dy) 有没有非墙邻居.
     * 用来防止两条走廊平行贴边.
     */
    private fun hasPerpNeighbor(
        grid: Array<Array<DungeonNode>>,
        nx: Int, ny: Int, dx: Int, dy: Int, w: Int, h: Int
    ): Boolean {
        val p1x = nx - dy; val p1y = ny + dx
        val p2x = nx + dy; val p2y = ny - dx
        if (p1y in 0 until h && p1x in 0 until w && grid[p1y][p1x].type != NodeType.WALL) return true
        if (p2y in 0 until h && p2x in 0 until w && grid[p2y][p2x].type != NodeType.WALL) return true
        return false
    }

    /**
     * 房间 (rx, ry) 周围 5×5 范围 (除来路方向 2 格深) 必须全是墙, 防止房间紧贴.
     * (fromDx, fromDy) = 走廊进入房间的方向, 这两格自然不是墙, 要排除掉.
     */
    private fun hasRoomClearance(
        grid: Array<Array<DungeonNode>>,
        rx: Int, ry: Int, fromDx: Int, fromDy: Int, w: Int, h: Int
    ): Boolean {
        for (cy in ry - 2..ry + 2) {
            for (cx in rx - 2..rx + 2) {
                if (cx == rx && cy == ry) continue
                val relX = cx - rx; val relY = cy - ry                      //相对坐标
                if (relX == -fromDx && relY == -fromDy) continue            // 来路紧邻格
                if (relX == -fromDx * 2 && relY == -fromDy * 2) continue    // 来路再前一格
                if (cy !in 0 until h || cx !in 0 until w) continue
                if (grid[cy][cx].type != NodeType.WALL) return false
            }
        }
        return true
    }

    /** 边界检查 — 比格子真实范围内缩 MARGIN+1, 保证地图四周有黑边. */
    private fun inBounds(x: Int, y: Int, w: Int, h: Int): Boolean =
        x in MARGIN + 1 until w - MARGIN - 1 && y in MARGIN + 1 until h - MARGIN - 1

    // 特殊房间 (楼梯 + Boss)

    /**
     * 选出三个特殊房间的下标: (上楼梯, 下楼梯, Boss房).
     * 规则:
     *   - 上楼梯: 永远在入口 (索引 0)
     *   - 下楼梯: gen×3 + 入口曼哈顿距离 最大 的房间 (代次+距离双重最远)
     *   - Boss房: 代次前 5 大里离下楼梯最近的; 房间不够时为 null
     * rooms 元素少于 2 时返回 null (没法选).
     */
    private fun pickSpecialIndices(rooms: List<RoomNode>): Triple<Int, Int, Int?>? {
        if (rooms.size < 2) return null
        val upIdx = 0
        val downIdx = rooms.indices.filter { it != 0 }
            .maxByOrNull { rooms[it].gen * 3 + manhattan(rooms[it], rooms[0]) }
            ?: return null
        val bossIdx = rooms.indices
            .filter { it != 0 && it != downIdx }
            .sortedByDescending { rooms[it].gen }
            .take(5)
            .minByOrNull { manhattan(rooms[it], rooms[downIdx]) }
        return Triple(upIdx, downIdx, bossIdx)
    }

    /** 把楼梯 + Boss 三种节点真正写入 grid. 选址由 [pickSpecialIndices] 完成. */
    private fun placeSpecialRooms(grid: Array<Array<DungeonNode>>, rooms: List<RoomNode>, layer: Int) {
        val (upIdx, downIdx, bossIdx) = pickSpecialIndices(rooms) ?: return

        grid[rooms[upIdx].y][rooms[upIdx].x] = DungeonNode(NodeType.STAIRS, NodeEvent.STAIRS_UP)
        grid[rooms[downIdx].y][rooms[downIdx].x] = DungeonNode(NodeType.STAIRS, NodeEvent.STAIRS_DOWN)
        if (bossIdx != null) {
            // Boss 等级 = 楼层 × 3 + 1; 没设计的层级 (bossForLevel 返回 null) 就不放 Boss 房
            val bossLevel = layer * 3 + 1
            val boss = MonsterRegistry.bossForLevel(bossLevel)
            if (boss != null) {
                grid[rooms[bossIdx].y][rooms[bossIdx].x] = DungeonNode(
                    NodeType.BOSS_ROOM, NodeEvent.BOSS_FIGHT, monsterIds = listOf(boss.id)
                )
            }
        }
    }

    // 事件分配 — 普通房间随机刷

    /**
     * 给"非特殊房间"挂事件. 分布: 50% 战斗 / 20% 守护宝藏 / 20% 陷阱 / 10% 解谜.
     * specials = 已标记的上下楼+Boss索引, 跳过这些.
     */
    private fun assignEvents(
        grid: Array<Array<DungeonNode>>, layer: Int,
        rooms: List<RoomNode>, specials: Set<Int>
    ) {
        for (i in rooms.indices) {
            if (i in specials) continue
            val r = rooms[i]; val cell = grid[r.y][r.x]
            if (cell.type == NodeType.WALL) continue   // 被楼梯/Boss 覆盖过的, 跳过
            grid[r.y][r.x] = when (Random.nextInt(100)) {
                in 0..49  -> randomEncounterCell(cell, layer)
                in 50..69 -> randomGuardedLootCell(cell, layer)
                in 70..89 -> cell.copy(event = NodeEvent.TRAP)
                else      -> randomPuzzleCell(cell)
            }
        }
        // 走廊不刷事件 — 玩家只会在房间里停下
    }

    /** 战斗房间: 从本层怪物池里随机抽 1-4 只怪. */
    private fun randomEncounterCell(cell: DungeonNode, layer: Int): DungeonNode {
        val pool = monsterPoolForLayer(layer)
        if (pool.isEmpty()) return cell.copy(event = NodeEvent.TRAP)   // 兜底: 没怪可用就改陷阱
        val count = Random.nextInt(1, 5)
        val ids = List(count) { pool.random().id }
        return cell.copy(event = NodeEvent.ENCOUNTER, monsterIds = ids)
    }

    /** 本层可用的普通怪物池 (排除 Boss). 等级范围 = 楼层×2 ±1, 越深的层怪越强. */
    private fun monsterPoolForLayer(layer: Int): List<Monster> {
        val minL = (layer * 2 - 1).coerceAtLeast(1)
        val maxL = layer * 2 + 1
        return MonsterRegistry.byLevelRange(minL, maxL)
    }

    /** 解谜房间: 没怪, 通过 INT 检定后从 lootTable 摇一次掉落. */
    private fun randomPuzzleCell(cell: DungeonNode): DungeonNode {
        val loot = mapOf(
            "magic_crystal" to 70, "ancient_coin" to 50, "spider_silk" to 30,
            "dark_shard" to 25, "minotaur_horn" to 12, "lich_phylactery" to 5
        )
        return cell.copy(event = NodeEvent.PUZZLE, lootTable = loot)
    }

    /** 守护宝藏: 1-4 只怪挡门, 击败后开宝箱. F2 额外有 30% 几率掉主线"苔纹剑" (保底见 ExplorationEngine). */
    private fun randomGuardedLootCell(cell: DungeonNode, layer: Int): DungeonNode {
        val pool = monsterPoolForLayer(layer)
        val loot = mutableMapOf(
            "iron_ore" to 70, "monster_bone" to 50, "magic_crystal" to 30,
            "ancient_coin" to 25, "spider_silk" to 20, "dark_shard" to 12
        )
        if (layer == 2) loot[QUEST_EPIC_SWORD_ID] = 30
        if (pool.isEmpty()) return cell.copy(event = NodeEvent.LOOT, lootTable = loot)
        val count = Random.nextInt(1, 5)
        val ids = List(count) { pool.random().id }
        return cell.copy(event = NodeEvent.GUARDED_LOOT, monsterIds = ids, lootTable = loot)
    }

    /** 曼哈顿距离 |dx| + |dy|. 网格地图用这个比欧几里得快 (无 sqrt) 且符合玩家移动感受 (上下左右). */
    private fun manhattan(a: RoomNode, b: RoomNode): Int = abs(a.x - b.x) + abs(a.y - b.y)
}
