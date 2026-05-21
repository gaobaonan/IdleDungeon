package com.example.idledungeon.engine

import com.example.idledungeon.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * DungeonGenerator 完全随机 (无 seed), 测试**只断言不变量**, 不断言具体地图.
 *
 * 核心不变量: 全图是以入口为根的树 → 入口必能 BFS 到下楼梯.
 */
class DungeonGeneratorTest {

    @Test
    fun `单次生成核心不变量`() {
        verifyInvariants(DungeonGenerator.generate(layer = 1))
    }

    // 跑 30 次, 把"无 seed 不可重现"的风险用统计覆盖.
    @Test
    fun `30次都满足不变量`() {
        repeat(30) { verifyInvariants(DungeonGenerator.generate(layer = 1)) }
    }

    private fun verifyInvariants(floor: DungeonFloor) {
        val entry = findCell(floor, NodeEvent.STAIRS_UP)
        assertNotNull("应有上楼梯", entry)
        val exit = findCell(floor, NodeEvent.STAIRS_DOWN)
        assertNotNull("应有下楼梯", exit)
        val reachable = bfs(floor, entry!!)
        assertTrue("入口应能到达下楼梯", exit!! in reachable)
    }

    private fun findCell(floor: DungeonFloor, event: NodeEvent): Pair<Int, Int>? {
        for (y in floor.grid.indices) for (x in floor.grid[y].indices) {
            if (floor.grid[y][x].event == event) return x to y
        }
        return null
    }

    // 标准 BFS, 只穿越非墙格. 用来验证连通性.
    private fun bfs(floor: DungeonFloor, start: Pair<Int, Int>): Set<Pair<Int, Int>> {
        val seen = mutableSetOf(start)
        val queue = ArrayDeque<Pair<Int, Int>>().apply { add(start) }
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
                val nx = x + dx; val ny = y + dy
                if (ny !in floor.grid.indices || nx !in floor.grid[0].indices) continue
                if (nx to ny in seen) continue
                if (floor.grid[ny][nx].type == NodeType.WALL) continue
                seen.add(nx to ny)
                queue.add(nx to ny)
            }
        }
        return seen
    }
}
