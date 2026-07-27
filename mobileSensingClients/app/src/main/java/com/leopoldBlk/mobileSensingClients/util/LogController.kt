package com.leopoldBlk.mobileSensingClients.util

import android.util.Log

object LogController {
    private var activeLogging: Boolean = false

    fun log(logLevel: Int, tag: String, message: String) {
        when (logLevel) {
            Log.DEBUG -> Log.d(tag, message)
            Log.INFO -> Log.i(tag, message)
            Log.ERROR -> Log.e(tag, message)
            Log.VERBOSE -> Log.v(tag, message)
            Log.WARN -> Log.w(tag, message)
        }
    }

    fun setActiveLogging(activeLogging: Boolean) {
        this.activeLogging = activeLogging
    }
}