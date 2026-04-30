package com.example.idledungeon.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.idledungeon.MainActivity
import com.example.idledungeon.R

/**
 * 探险事件后台通知工具.
 * - createChannel: App 启动时调用一次
 * - notify: 收到事件且 App 在后台时调用
 */
object NotificationHelper {

    const val CHANNEL_ID = "expedition_events"
    private const val CHANNEL_NAME = "探险事件"

    // 不同事件用不同通知 ID, 避免互相覆盖
    private const val NOTIF_ID_BOSS = 1001
    private const val NOTIF_ID_FLOOR = 1002
    private const val NOTIF_ID_RETREAT = 1003

    /** 注册通知 Channel (Android 8+ 必需). 重复创建无副作用. */
    fun createChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Boss 战胜 / 上下楼 / 队伍撤退"
        }
        mgr.createNotificationChannel(channel)
    }

    /** 投递事件通知. App 在前台时不应调用 (UI 已展示) */
    @SuppressLint("MissingPermission")  // 权限已在 hasPostPermission 里检查
    fun notify(context: Context, event: ExpeditionEvent) {
        if (!hasPostPermission(context)) return  // Android 13+ 用户拒绝过就静默跳过

        val (title, text, id) = when (event) {
            is ExpeditionEvent.BossDefeated -> Triple(
                "Boss 击败！",
                "第 ${event.layer} 层 ${event.bossName} 倒下",
                NOTIF_ID_BOSS
            )
            is ExpeditionEvent.FloorChanged -> Triple(
                "楼层变化",
                "队伍下到第 ${event.newLayer} 层",
                NOTIF_ID_FLOOR
            )
            is ExpeditionEvent.Retreated -> Triple(
                "队伍撤退",
                event.reason,
                NOTIF_ID_RETREAT
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(id, notif)
    }

    private fun hasPostPermission(context: Context): Boolean {
        // Android 13 (API 33) 之前不需要运行时权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
