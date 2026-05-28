package com.gaotong.idledungeon.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 探险中后台需要推通知的事件
sealed class ExpeditionEvent {
    data class BossDefeated(val bossName: String, val layer: Int) : ExpeditionEvent()
    data class FloorChanged(val newLayer: Int) : ExpeditionEvent()
    data class Retreated(val reason: String) : ExpeditionEvent()
}

// 引擎发, App 订阅. 缓冲 16 防丢, 满了挤旧的.
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
