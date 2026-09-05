package com.leopoldBlk.mobileSensingClients.util.controller

import android.util.Log

// Checked (final)
object LogController {
    private var activeLogging: Boolean = false
    private var activeLoggingGeneralFunctionalityEvaluation: Boolean = false

    fun log(logLevel: Int, tag: String, message: String) {
        if (activeLogging) {
            when (logLevel) {
                Log.DEBUG -> Log.d(tag, message)
                Log.INFO -> Log.i(tag, message)
                Log.ERROR -> Log.e(tag, message)
                Log.VERBOSE -> Log.v(tag, message)
                Log.WARN -> Log.w(tag, message)
            }
        }
    }

    fun logGeneralFunctionalityEvaluation(logLevel: Int, tag: String, message: String) {
        if (activeLoggingGeneralFunctionalityEvaluation) {
            if (message.length > 4000) {

                val messageChunks = message.chunked(4000)
                var chunkNumber: Int = 0

                for (chunk in messageChunks){
                    when (logLevel) {
                        Log.DEBUG -> Log.d(tag, "[Chunk $chunkNumber] $chunk")
                        Log.INFO -> Log.i(tag, "[Chunk $chunkNumber] $chunk")
                        Log.ERROR -> Log.e(tag, "[Chunk $chunkNumber] $chunk")
                        Log.VERBOSE -> Log.v(tag, "[Chunk $chunkNumber] $chunk")
                        Log.WARN -> Log.w(tag, "[Chunk $chunkNumber] $chunk")
                    }
                    chunkNumber = chunkNumber + 1
                }
            } else {
                when (logLevel) {
                    Log.DEBUG -> Log.d(tag, message)
                    Log.INFO -> Log.i(tag, message)
                    Log.ERROR -> Log.e(tag, message)
                    Log.VERBOSE -> Log.v(tag, message)
                    Log.WARN -> Log.w(tag, message)
                }
            }
        }
    }

    fun setActiveLogging(activeLogging: Boolean) {
        this.activeLogging = activeLogging
    }

    fun setActiveLoggingGeneralFunctionalityEvaluation(activeLoggingGeneralFunctionalityEvaluation: Boolean) {
        this.activeLoggingGeneralFunctionalityEvaluation =
            activeLoggingGeneralFunctionalityEvaluation
    }
}