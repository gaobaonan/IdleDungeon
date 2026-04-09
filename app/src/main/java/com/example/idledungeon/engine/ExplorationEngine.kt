package com.example.idledungeon.engine

import com.example.idledungeon.model.*
import com.example.idledungeon.viewmodel.GameUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 探索引擎 - 协程驱动的放置式探索
 * 每隔一定时间推进一格，处理事件，消耗补给
 */
class ExplorationEngine {

    companion object {
        /** 每层基础推进间隔 (毫秒) */
        private const val BASE_INTERVAL_MS = 10_000L
        /** 补给消耗: 普通格 / 陷阱格 / 战斗格 */
        private const val SUPPLY_NORMAL = 1
        private const val SUPPLY_TRAP = 3
        private const val SUPPLY_COMBAT = 2
    }

    /**
     * 运行探索循环 (在 viewModelScope 中启动)
     */
    suspend fun run(session: ExpeditionSession, stateFlow: MutableStateFlow<GameUiState>) {
        val state = stateFlow.value
        val layer = session.currentLayer

        // 生成当前楼层地图 (如果还没有)
        val floor = state.currentFloor ?: DungeonGenerator.generate(layer)
        stateFlow.update { it.copy(currentFloor = floor) }

        val interval = BASE_INTERVAL_MS * layer // 更深层 = 更慢
        var currentSession = session.copy(isExploring = true)
        var currentFloor = floor

        // 简单的寻路: 从当前位置向楼梯方向移动
        while (currentSession.isExploring && currentSession.currentSupply > 0) {
            delay(interval)

            // 1. 移动到下一个可通行节点
            val (nx, ny) = findNextStep(currentFloor, currentSession.posX, currentSession.posY)
            if (nx == currentSession.posX && ny == currentSession.posY) {
                // 无路可走 → 结束
                break
            }

            // 2. 标记已探索
            val updatedGrid = currentFloor.grid.mapIndexed { y, row ->
                row.mapIndexed { x, node ->
                    if (x == nx && y == ny) node.copy(explored = true) else node
                }
            }
            currentFloor = currentFloor.copy(grid = updatedGrid)

            // 3. 处理当前节点事件
            val node = currentFloor.grid[ny][nx]
            val supplyCost: Int
            val log = mutableListOf<String>()

            when (node.event) {
                NodeEvent.NONE -> {
                    supplyCost = SUPPLY_NORMAL
                    log.add("Moved to ($nx, $ny) - nothing here / 平安无事")
                }
                NodeEvent.TRAP -> {
                    supplyCost = SUPPLY_TRAP
                    log.add("Trap! / 陷阱！ Supply -$SUPPLY_TRAP")
                    // TODO: 对队伍造成伤害
                }
                NodeEvent.ENCOUNTER -> {
                    supplyCost = SUPPLY_COMBAT
                    val heroes = getPartyHeroes(stateFlow.value, currentSession.party)
                    if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                        val result = CombatEngine.fight(heroes, node.monsterIds)
                        log.addAll(result.log)
                        currentSession = currentSession.copy(
                            backpack = currentSession.backpack + result.droppedItems
                        )
                        // TODO: 更新英雄 HP 状态
                        if (!result.victory) {
                            log.add("Party wiped! Retreating... / 全灭！撤退...")
                            currentSession = currentSession.copy(isExploring = false)
                        }
                    }
                }
                NodeEvent.LOOT -> {
                    supplyCost = SUPPLY_NORMAL
                    val loot = node.lootTable.filter { (_, chance) ->
                        kotlin.random.Random.nextInt(100) < chance
                    }.keys.toList()
                    currentSession = currentSession.copy(backpack = currentSession.backpack + loot)
                    log.add("Found loot / 发现宝物: ${loot.joinToString()}")
                }
                NodeEvent.BOSS_FIGHT -> {
                    supplyCost = SUPPLY_COMBAT * 2
                    val heroes = getPartyHeroes(stateFlow.value, currentSession.party)
                    if (node.monsterIds.isNotEmpty() && heroes.isNotEmpty()) {
                        val result = CombatEngine.fight(heroes, node.monsterIds)
                        log.addAll(result.log)
                        currentSession = currentSession.copy(
                            backpack = currentSession.backpack + result.droppedItems
                        )
                        if (!result.victory) {
                            currentSession = currentSession.copy(isExploring = false)
                        }
                    }
                }
                NodeEvent.STAIRS_DOWN -> {
                    supplyCost = SUPPLY_NORMAL
                    log.add("Found stairs down / 发现下行楼梯！")
                    // 生成下一层
                    val newLayer = currentSession.currentLayer + 1
                    currentFloor = DungeonGenerator.generate(newLayer)
                    currentSession = currentSession.copy(
                        currentLayer = newLayer, posX = 0, posY = 0
                    )
                }
                NodeEvent.STAIRS_UP -> {
                    supplyCost = SUPPLY_NORMAL
                    log.add("Found stairs up / 发现上行楼梯")
                }
            }

            // 4. 更新状态
            currentSession = currentSession.copy(
                posX = nx,
                posY = ny,
                currentSupply = (currentSession.currentSupply - supplyCost).coerceAtLeast(0),
                combatLog = currentSession.combatLog + log
            )

            stateFlow.update {
                it.copy(
                    currentFloor = currentFloor,
                    expedition = currentSession,
                    gameState = it.gameState.copy(
                        deepestFloorReached = maxOf(it.gameState.deepestFloorReached, currentSession.currentLayer)
                    )
                )
            }

            // 5. 检查撤退条件
            if (currentSession.currentSupply <= 0) {
                currentSession = currentSession.copy(isExploring = false)
                stateFlow.update {
                    it.copy(
                        expedition = currentSession.copy(
                            combatLog = currentSession.combatLog + "Out of supplies! Retreating... / 补给耗尽！撤退..."
                        )
                    )
                }
            }
        }
    }

    /**
     * 简单寻路: 向最近的未探索可通行节点移动
     */
    private fun findNextStep(floor: DungeonFloor, cx: Int, cy: Int): Pair<Int, Int> {
        val directions = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0).shuffled()
        for ((dx, dy) in directions) {
            val nx = cx + dx
            val ny = cy + dy
            if (ny in floor.grid.indices && nx in floor.grid[0].indices) {
                val node = floor.grid[ny][nx]
                if (node.type != NodeType.WALL && !node.explored) {
                    return nx to ny
                }
            }
        }
        // 如果所有未探索方向都走过了，允许走已探索的格子
        for ((dx, dy) in directions) {
            val nx = cx + dx
            val ny = cy + dy
            if (ny in floor.grid.indices && nx in floor.grid[0].indices) {
                if (floor.grid[ny][nx].type != NodeType.WALL) {
                    return nx to ny
                }
            }
        }
        return cx to cy // 真的无路可走
    }

    private fun getPartyHeroes(state: GameUiState, party: List<PartySlot>): List<Hero> {
        return party.mapNotNull { slot -> state.heroes.find { it.id == slot.heroId } }
    }
}