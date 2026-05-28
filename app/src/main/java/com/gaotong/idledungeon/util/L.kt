package com.gaotong.idledungeon.util

import android.util.Log

// 日志统一从这里走, Logcat 过滤 IdleDungeon 就只看自己的.
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
