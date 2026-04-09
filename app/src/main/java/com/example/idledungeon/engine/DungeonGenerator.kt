package com.example.idledungeon.engine

import com.example.idledungeon.data.MonsterRegistry
import com.example.idledungeon.model.*
import kotlin.random.Random

/**
 * 地牢楼层生成器
 * 算法: 随机房间放置 + 走廊连接 + 保证两个楼梯之间可达
 */
object DungeonGenerator {

    private const val DEFAULT_WIDTH = 30
    private const val DEFAULT_HEIGHT = 20

    /**
     * 生成一层地牢
     * @param layer 楼层编号 (影响怪物等级和事件概率)
     */
    fun generate(layer: Int, width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT): DungeonFloor {
        // 1. 初始化全墙壁
        val grid = Array(height) { Array(width) { DungeonNode(NodeType.WALL) } }

        // 2. 随机放置房间
        val rooms = placeRooms(grid, width, height)

        // 3. 用走廊连接所有房间 (保证可达性)
        connectRooms(grid, rooms)

        // 4. 放置两个楼梯 (分别在最远的两个房间)
        placeStairs(grid, rooms)

        // 5. 为走廊和房间分配事件
        assignEvents(grid, layer)

        // 6. 选一个房间放 Boss
        placeBossRoom(grid, rooms, layer)

        return DungeonFloor(
            layer = layer,
            width = width,
            height = height,
            grid = grid.map { row -> row.toList() }
        )
    }

    // ─── 内部实现 ───

    private data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
        val centerX get() = x + w / 2
        val centerY get() = y + h / 2
    }

    private fun placeRooms(grid: Array<Array<DungeonNode>>, width: Int, height: Int): List<Room> {
        val rooms = mutableListOf<Room>()
        val attempts = 30
        repeat(attempts) {
            val rw = Random.nextInt(4, 8)
            val rh = Random.nextInt(3, 6)
            val rx = Random.nextInt(1, width - rw - 1)
            val ry = Random.nextInt(1, height - rh - 1)
            val candidate = Room(rx, ry, rw, rh)

            // 检查是否和已有房间重叠
            val overlaps = rooms.any { existing ->
                candidate.x < existing.x + existing.w + 1 &&
                candidate.x + candidate.w + 1 > existing.x &&
                candidate.y < existing.y + existing.h + 1 &&
                candidate.y + candidate.h + 1 > existing.y
            }
            if (!overlaps) {
                // 在 grid 中标记房间
                for (dy in 0 until rh) {
                    for (dx in 0 until rw) {
                        grid[ry + dy][rx + dx] = DungeonNode(NodeType.ROOM)
                    }
                }
                rooms.add(candidate)
            }
        }
        return rooms
    }

    private fun connectRooms(grid: Array<Array<DungeonNode>>, rooms: List<Room>) {
        for (i in 0 until rooms.size - 1) {
            val a = rooms[i]
            val b = rooms[i + 1]
            // L 形走廊: 先水平后垂直
            carveHorizontal(grid, a.centerY, a.centerX, b.centerX)
            carveVertical(grid, b.centerX, a.centerY, b.centerY)
        }
    }

    private fun carveHorizontal(grid: Array<Array<DungeonNode>>, y: Int, x1: Int, x2: Int) {
        val range = if (x1 <= x2) x1..x2 else x2..x1
        for (x in range) {
            if (y in grid.indices && x in grid[0].indices && grid[y][x].type == NodeType.WALL) {
                grid[y][x] = DungeonNode(NodeType.CORRIDOR)
            }
        }
    }

    private fun carveVertical(grid: Array<Array<DungeonNode>>, x: Int, y1: Int, y2: Int) {
        val range = if (y1 <= y2) y1..y2 else y2..y1
        for (y in range) {
            if (y in grid.indices && x in grid[0].indices && grid[y][x].type == NodeType.WALL) {
                grid[y][x] = DungeonNode(NodeType.CORRIDOR)
            }
        }
    }

    private fun placeStairs(grid: Array<Array<DungeonNode>>, rooms: List<Room>) {
        if (rooms.size < 2) return
        val first = rooms.first()
        val last = rooms.last()
        grid[first.centerY][first.centerX] = DungeonNode(NodeType.STAIRS, NodeEvent.STAIRS_UP)
        grid[last.centerY][last.centerX] = DungeonNode(NodeType.STAIRS, NodeEvent.STAIRS_DOWN)
    }

    private fun assignEvents(grid: Array<Array<DungeonNode>>, layer: Int) {
        for (y in grid.indices) {
            for (x in grid[y].indices) {
                val node = grid[y][x]
                if (node.type == NodeType.CORRIDOR) {
                    grid[y][x] = when (Random.nextInt(100)) {
                        in 0..4 -> node.copy(event = NodeEvent.TRAP)
                        in 5..14 -> {
                            val monsters = MonsterRegistry.byLevelRange(1, layer * 2)
                            node.copy(
                                event = NodeEvent.ENCOUNTER,
                                monsterIds = if (monsters.isNotEmpty()) listOf(monsters.random().id) else emptyList()
                            )
                        }
                        else -> node // NONE
                    }
                } else if (node.type == NodeType.ROOM && node.event == NodeEvent.NONE) {
                    grid[y][x] = when (Random.nextInt(100)) {
                        in 0..19 -> node.copy(
                            event = NodeEvent.LOOT,
                            lootTable = mapOf("iron_ore" to 50, "monster_bone" to 30)
                        )
                        in 20..34 -> {
                            val monsters = MonsterRegistry.byLevelRange(1, layer * 2)
                            node.copy(
                                event = NodeEvent.ENCOUNTER,
                                monsterIds = if (monsters.isNotEmpty()) listOf(monsters.random().id) else emptyList()
                            )
                        }
                        else -> node
                    }
                }
            }
        }
    }

    private fun placeBossRoom(grid: Array<Array<DungeonNode>>, rooms: List<Room>, layer: Int) {
        if (rooms.size < 3) return
        val bossRoom = rooms[rooms.size / 2] // 中间的房间做 Boss 房
        val bossMonsters = MonsterRegistry.all().filter { it.id.startsWith("boss_") }
        val bossId = bossMonsters.randomOrNull()?.id ?: return

        grid[bossRoom.centerY][bossRoom.centerX] = DungeonNode(
            type = NodeType.BOSS_ROOM,
            event = NodeEvent.BOSS_FIGHT,
            monsterIds = listOf(bossId)
        )
    }
}