package com.leopoldBlk.mobileSensingClients.util.controller

import android.os.Environment
import com.leopoldBlk.mobileSensingClients.util.DataStore
import java.time.LocalDateTime

object LoggingDirectoryController {
    private val evaluationLogDirectory: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .toString() + "/MCS_Application/Evaluation/"

    private val evaluationPositionalBeaconsLogDirectory: String =
        evaluationLogDirectory + "DeterminationOfCrowdDensity/TransmissionOfDensityData/D2dCommunication/"

    public fun getEvaluationPositionalBeaconsLogFilePath(
        evaluationPositionalBeaconsLogFileName: String
    ): String {
        val evaluationLogSuffix: String =
            (DataStore.clientIdHexString + "_" + LocalDateTime.now()).replace("T", "_")
                .replace(":", "-")

        val evaluationPositionalBeaconsLogFilePath: String =
            evaluationPositionalBeaconsLogDirectory + evaluationPositionalBeaconsLogFileName + "_" + evaluationLogSuffix + ".txt"

        return evaluationPositionalBeaconsLogFilePath
    }
}