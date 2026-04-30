package com.example.idledungeon

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.idledungeon.notification.ExpeditionEventBus
import com.example.idledungeon.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级 Application.
 *
 * 职责:
 *   - 启动时注册通知 channel
 *   - 用 ProcessLifecycleOwner 监听 App 整体前后台 (任何 Activity 在前台 = foreground)
 *   - 起一个长生命周期 CoroutineScope, 持续订阅 ExpeditionEventBus
 *   - 收到事件时, 仅在 App 处于后台时投递通知 (前台 UI 已经显示, 重复推送会噪)
 *
 * 注意: App 进程被划掉/系统强杀后, 此协程一并死亡, 不会再发通知.
 *       这正是路线 A 的限制 (毕设可接受). 真要"划掉也通知"需要 Foreground Service.
 */
class App : Application(), DefaultLifecycleObserver {

    @Volatile private var inBackground = false

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super<Application>.onCreate()

        // 1. 注册通知 channel (Android 8+)
        NotificationHelper.createChannel(this)

        // 2. 监听 App 整体前后台 (所有 Activity 都不可见时进入 background)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // 3. 长期订阅事件总线; 后台时投递通知, 前台直接忽略
        appScope.launch {
            ExpeditionEventBus.events.collect { event ->
                if (inBackground) {
                    NotificationHelper.notify(this@App, event)
                }
            }
        }
    }

    // 前台 ↔ 后台 切换钩子 (ProcessLifecycleOwner)
    override fun onStart(owner: LifecycleOwner) { inBackground = false }
    override fun onStop(owner: LifecycleOwner)  { inBackground = true }
}
