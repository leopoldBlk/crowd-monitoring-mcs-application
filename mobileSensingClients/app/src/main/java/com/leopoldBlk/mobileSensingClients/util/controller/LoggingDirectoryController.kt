package com.leopoldBlk.mobileSensingClients.util.controller

import android.os.Environment
import com.leopoldBlk.mobileSensingClients.util.DataStore
import java.time.LocalDateTime

object LoggingDirectoryController {
    private val evaluationLogDirectory: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .toString() + "/MCS_Application/Evaluation/"

    private val evaluationD2dCommunicationLogDirectory: String =
        evaluationLogDirectory + "DeterminationOfCrowdDensity/TransmissionOfDensityData/D2dCommunication/"

    private val evaluationInfrastructureBasedCommunicationLogDirectory: String =
        evaluationLogDirectory + "DeterminationOfCrowdDensity/TransmissionOfDensityData/InfrastructureBasedCommunication/"

    private val evaluationSelectionOfEdgeClientsLogDirectory: String =
        evaluationLogDirectory + "DeterminationOfCrowdDensity/SelectionOfEdgeClients/"

    public fun getEvaluationD2dCommunicationLogFilePath(
        evaluationD2dCommunicationLogFileName: String
    ): String {
        val evaluationLogSuffix: String =
            (DataStore.clientIdHexString + "_" + LocalDateTime.now()).replace("T", "_")
                .replace(":", "-")

        val evaluationD2dCommunicationLogFilePath: String =
            evaluationD2dCommunicationLogDirectory + evaluationD2dCommunicationLogFileName + "_" + evaluationLogSuffix + ".txt"

        return evaluationD2dCommunicationLogFilePath
    }

    public fun getEvaluationInfrastructureBasedCommunicationLogFilePath(
        evaluationInfrastructureBasedCommunicationLogFileName: String
    ): String {
        val evaluationLogSuffix: String =
            (DataStore.clientIdHexString + "_" + LocalDateTime.now()).replace("T", "_")
                .replace(":", "-")

        val evaluationInfrastructureBasedCommunicationLogFilePath: String =
            evaluationInfrastructureBasedCommunicationLogDirectory + evaluationInfrastructureBasedCommunicationLogFileName + "_" + evaluationLogSuffix + ".txt"

        return evaluationInfrastructureBasedCommunicationLogFilePath
    }

    public fun getEvaluationSelectionOfEdgeClientsLogFilePath(
        evaluationSelectionOfEdgeClientsLogFileName: String
    ): String {
        val evaluationLogSuffix: String =
            (DataStore.clientIdHexString + "_" + LocalDateTime.now()).replace("T", "_")
                .replace(":", "-")

        val evaluationSelectionOfEdgeClientsLogFilePath: String =
            evaluationSelectionOfEdgeClientsLogDirectory + evaluationSelectionOfEdgeClientsLogFileName + "_" + evaluationLogSuffix + ".txt"

        return evaluationSelectionOfEdgeClientsLogFilePath
    }
}