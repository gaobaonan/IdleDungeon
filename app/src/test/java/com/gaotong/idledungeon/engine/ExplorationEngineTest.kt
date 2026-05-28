package com.gaotong.idledungeon.engine

import com.gaotong.idledungeon.model.*
import com.gaotong.idledungeon.viewmodel.GameUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * ExplorationEngine.run() 是 suspend 函数 (协程 + delay), 测试用 runTest 包裹.
 * runTest 提供虚拟时钟: 真实的 delay(1200ms) 在测试里几乎瞬时.
 *
 * 一个端到端测试, 覆盖最关键的"不死循环"契约 — 探险一定终止.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorationEngineTest {

    @Test
    fun `探险一定终止`() = runTest {
        val hero = testHero()
        val session = ExpeditionSession(
            dungeonId = "test",
            currentLayer = 1,
            party = listOf(PartySlot(hero.id, Position.FRONT)),
            currentSupply = 30,
            maxSupply = 30
        )
        val stateFlow = MutableStateFlow(GameUiState(heroes = listOf(hero)))

        ExplorationEngine().run(session, stateFlow)

        val final = stateFlow.value.expedition
        assertNotNull("应保留 session", final)
        assertFalse("探险应已终止", final!!.isExploring)
        assertTrue("应有探险日志", final.combatLog.isNotEmpty())
    }

    private fun testHero(): Hero = Hero(
        id = "h", name = "T", portraitUri = null,
        heroClass = HeroClass.WARRIOR,
        currentHp = 100, maxHp = 100,
        baseStats = mapOf(
            StatType.STR to 10, StatType.DEX to 10,
            StatType.INT to 5, StatType.FTH to 5
        )
    )
}
