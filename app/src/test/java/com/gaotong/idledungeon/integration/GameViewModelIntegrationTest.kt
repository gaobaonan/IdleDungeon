package com.gaotong.idledungeon.integration

import com.gaotong.idledungeon.data.SaveRepository
import com.gaotong.idledungeon.engine.ExplorationEngine
import com.gaotong.idledungeon.model.ExpeditionSession
import com.gaotong.idledungeon.model.GameState
import com.gaotong.idledungeon.model.Hero
import com.gaotong.idledungeon.model.HeroClass
import com.gaotong.idledungeon.model.PartySlot
import com.gaotong.idledungeon.model.Position
import com.gaotong.idledungeon.model.StatType
import com.gaotong.idledungeon.viewmodel.GameUiState
import com.gaotong.idledungeon.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 集成测试 — 跨 ViewModel + ExplorationEngine + SaveRepository 三层验证关键流程.
 *
 * 与单元测试的区别: 这里不 mock 引擎或假装行为, 而是让真实组件在 Fake Repository
 * 提供的初始状态上跑完整流程, 断言最终状态.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelIntegrationTest {

    @Before
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    // ① 离线补偿端到端: 一个进行中的探险 + 旧时间戳, VM 应自动调引擎快进并更新状态.
    //   跨 VM(init → checkOfflineCatchUp) + ExplorationEngine(快进模式) + FakeRepo(提供存档).
    @Test
    fun `离线补偿应推进探险并刷新时间戳`() = runTest {
        val hero = testHero()
        val session = ExpeditionSession(
            dungeonId = "test",
            currentLayer = 1,
            party = listOf(PartySlot(hero.id, Position.FRONT)),
            currentSupply = 100,
            maxSupply = 100,
            isExploring = true
        )
        // 时间戳只要够让 catch-up 触发即可 (steps > 0). 100 补给会让引擎在 ~100 步内
        // 因补给耗尽自然终止, 远低于 maxSteps 上限. 此处用 1 小时是留一个宽裕的安全边际.
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        val saved = GameUiState(
            gameState = GameState(hasStarted = true, lastActiveAt = oneHourAgo),
            heroes = listOf(hero),
            expedition = session
        )

        val vm = GameViewModel(FakeSaveRepository(loadResult = saved), ExplorationEngine())
        advanceUntilIdle()

        val final = vm.uiState.value
        assertTrue("lastActiveAt 应被推到当前", final.gameState.lastActiveAt > oneHourAgo)
        assertTrue("探险应被推进 (至少走过 1 格或已结束)",
            final.gameState.roomsExplored > 0 || final.expedition?.isExploring == false)
    }

    // ② 新游戏端到端: requestNewGame + executePendingNewGame → 状态切换 → debounced 自动保存被触发.
    //   跨 VM(状态更新 + debounced autosave) + FakeRepo(计数保存调用次数).
    @Test
    fun `新游戏应触发自动保存`() = runTest {
        val fakeRepo = FakeSaveRepository()
        val vm = GameViewModel(fakeRepo, ExplorationEngine())
        advanceUntilIdle()                              // 让 init 加载完成

        vm.requestNewGame()
        vm.executePendingNewGame()
        advanceUntilIdle()                              // 让 debounce(500ms) 触发 → 调 save

        assertTrue("hasStarted 应为 true", vm.uiState.value.gameState.hasStarted)
        assertTrue("自动保存应被触发至少一次", fakeRepo.saveCallCount > 0)
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

/** 测试用假 Repository — load 可配置返回值, save / clear 计数, 用于断言被调到几次. */
private class FakeSaveRepository(
    private val loadResult: GameUiState? = null
) : SaveRepository {
    var saveCallCount = 0
    var clearCallCount = 0
    override suspend fun load(): GameUiState? = loadResult
    override suspend fun save(state: GameUiState) { saveCallCount++ }
    override suspend fun clear() { clearCallCount++ }
}
