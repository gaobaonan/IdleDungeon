package com.example.idledungeon.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 探险中需要后台提醒的关键事件.
 * Boss 击败 / 楼层变化 / 撤退 三种.
 */
sealed class ExpeditionEvent {
    data class BossDefeated(val bossName: String, val layer: Int) : ExpeditionEvent()
    data class FloorChanged(val newLayer: Int) : ExpeditionEvent()
    data class Retreated(val reason: String) : ExpeditionEvent()
}

/**
 * 全局事件总线 (单例 SharedFlow).
 * 引擎用 [emit] 发, App 用 [events] 订阅.
 *
 * - extraBufferCapacity = 16: 即使没人订阅也不会丢
 * - DROP_OLDEST: 极端情况下旧事件先被挤出
 */
object ExpeditionEventBus {
    private val _events = MutableSharedFlow<ExpeditionEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ExpeditionEvent> = _events.asSharedFlow()

    fun emit(event: ExpeditionEvent) {
        _events.tryEmit(event)
    }
}
