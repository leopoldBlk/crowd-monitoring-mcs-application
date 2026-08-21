package com.leopoldBlk.mobileSensingClients.util.controller

import android.util.Log

// Checked (final)
object LogController {
    private var activeLogging: Boolean = false

    fun log(logLevel: Int, tag: String, message: String) {
        if(activeLogging){
            when (logLevel) {
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.ERROR -> Log.e(tag, message)
                Log.VERBOSE -> Log.v(tag, message)
                Log.WARN -> Log.w(tag, message)
            }
        }
    }

    fun setActiveLogging(activeLogging: Boolean) {
        this.activeLogging = activeLogging
    }
}