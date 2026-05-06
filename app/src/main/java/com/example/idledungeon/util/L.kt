package com.example.idledungeon.util

import android.util.Log

/**
 * 全局日志工具.
 *
 * 用法: `L.i("Combat", "Round $round started")`
 * 在 Logcat 用 `adb logcat -s IdleDungeon` 可只看本 App 的日志.
 *
 * 所有等级 (d/i/w/e) 都直接写入 Logcat — 毕设阶段不区分 Debug/Release,
 * 方便答辩演示时随时复盘任何问题.
 */
object L {
    private const val APP = "IdleDungeon"

    fun d(tag: String, msg: String) { Log.d("$APP/$tag", msg) }
    fun i(tag: String, msg: String) { Log.i("$APP/$tag", msg) }
    fun w(tag: String, msg: String, e: Throwable? = null) {
        if (e != null) Log.w("$APP/$tag", msg, e) else Log.w("$APP/$tag", msg)
    }
    fun e(tag: String, msg: String, e: Throwable? = null) {
        if (e != null) Log.e("$APP/$tag", msg, e) else Log.e("$APP/$tag", msg)
    }
}
