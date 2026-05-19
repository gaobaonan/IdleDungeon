package com.example.idledungeon

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.idledungeon.notification.ExpeditionEventBus
import com.example.idledungeon.notification.NotificationHelper
import com.example.idledungeon.util.L
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application(), DefaultLifecycleObserver {

    @Volatile private var inBackground = false

    private val crashHandler = CoroutineExceptionHandler { _, e ->
        L.e("App", "协程报错", e)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashHandler)

    override fun onCreate() {
        super<Application>.onCreate()
        L.i("App", "App.onCreate")

        NotificationHelper.createChannel(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        appScope.launch {
            ExpeditionEventBus.events.collect { event ->
                L.d("App", "收到 ExpeditionEvent: $event (inBackground=$inBackground)")
                if (inBackground) {
                    NotificationHelper.notify(this@App, event)
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        inBackground = false
        L.i("App", "进入前台")
    }
    override fun onStop(owner: LifecycleOwner) {
        inBackground = true
        L.i("App", "退到后台")
    }
}
