package com.example.idledungeon.viewmodel

import com.example.idledungeon.data.SaveRepository
import com.example.idledungeon.engine.ExplorationEngine
import com.example.idledungeon.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * GameViewModel 单元测试 — 演示 DI 抽象成接口后的可测试性.
 *
 * 关键点: SaveRepository 现在是 interface, 这里塞 FakeSaveRepository 替代真 Room,
 * 完全不需要 Android 环境、不需要 Hilt 启动. 也是接口化的最大收益证明.
 *
 * viewModelScope 默认用 Dispatchers.Main, 单测中没有 Main, 必须 setMain 替换成测试 dispatcher.
 * UnconfinedTestDispatcher 让协程一启动就同步执行, 测试不用 runTest/虚拟时间也能验断言.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 验证 ① — 新游戏流程: requestNewGame → executePendingNewGame → state.hasStarted=true
    @Test
    fun `新游戏后 hasStarted 为 true`() {
        val vm = GameViewModel(FakeSaveRepository(), ExplorationEngine())

        vm.requestNewGame()
        vm.executePendingNewGame()

        assertTrue("新游戏后 hasStarted 应为 true", vm.uiState.value.gameState.hasStarted)
    }

    // 验证 ② — DI 价值实证: 注入返回特定存档的假 Repo, VM 应使用它
    @Test
    fun `init 应使用 saveRepo load 返回的存档`() {
        val saved = GameUiState(gameState = GameState(hasStarted = true, playerName = "Loaded"))
        val vm = GameViewModel(FakeSaveRepository(loadResult = saved), ExplorationEngine())

        // UnconfinedTestDispatcher 让 init 协程同步跑完, 加载结果应已应用到 state
        assertEquals("VM 应使用 saveRepo.load 的结果", "Loaded", vm.uiState.value.gameState.playerName)
        assertTrue("hasStarted 应被同步过来", vm.uiState.value.gameState.hasStarted)
    }
}

/** 测试用假实现 — load 可配置, save/clear 记录是否被调过. */
private class FakeSaveRepository(
    private val loadResult: GameUiState? = null
) : SaveRepository {
    var saveCallCount = 0
    var clearCallCount = 0
    override suspend fun load(): GameUiState? = loadResult
    override suspend fun save(state: GameUiState) { saveCallCount++ }
    override suspend fun clear() { clearCallCount++ }
}
