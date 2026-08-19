package com.leopoldBlk.mobileSensingClients.util.controller

import com.leopoldBlk.mobileSensingClients.util.DataStore
import crowdDensityLibrary.dataClasses.PositionalBeacon
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer

object TransmissionDecisionController {
    private val evaluationSelectionOfEdgeClientEventLog: File =
        File(LoggingDirectoryController.getEvaluationSelectionOfEdgeClientsLogFilePath("SelectionOfEdgeClientEventLog"))

    private lateinit var evaluationSelectionOfEdgeClientEventLogFileWriter: FileWriter

    init {
        // Create directory for the log file if not already existing
        if (!File(evaluationSelectionOfEdgeClientEventLog.parent).exists()) {
            File(evaluationSelectionOfEdgeClientEventLog.parent).mkdirs()
        }

        // Create file writer which appends new logs to the log file and specifying the logging format
        evaluationSelectionOfEdgeClientEventLogFileWriter =
            FileWriter(evaluationSelectionOfEdgeClientEventLog, true)
        evaluationSelectionOfEdgeClientEventLogFileWriter.write("client id; was previous edge client; is new edge client; timestamp after update of the edge client role in milliseconds\n")
        evaluationSelectionOfEdgeClientEventLogFileWriter.flush()
    }

    public fun updateTransmissionDecision() {
        val newValueForNeighborWithLargerTransmissionBenefitExists: Boolean =
            neighborWithLargerTransmissionBenefitExists()
        val oldValueForNeighborWithLargerTransmissionBenefitExists: Boolean =
            DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.value!!.not()

        DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.postValue(!newValueForNeighborWithLargerTransmissionBenefitExists)
        val timestampAfterUpdateOfTheEdgeClientRole: Long =
            if (DataStore.trustedTimeClient != null) {
                DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
            } else {
                System.currentTimeMillis()
            }

        if (oldValueForNeighborWithLargerTransmissionBenefitExists != newValueForNeighborWithLargerTransmissionBenefitExists) {
            evaluationSelectionOfEdgeClientEventLogFileWriter.write("${DataStore.clientIdHexString}; ${!oldValueForNeighborWithLargerTransmissionBenefitExists}; ${!newValueForNeighborWithLargerTransmissionBenefitExists}; ${timestampAfterUpdateOfTheEdgeClientRole}\n")
            evaluationSelectionOfEdgeClientEventLogFileWriter.flush()
        }
    }

    public fun closeEvaluationSelectionOfEdgeClientEventLogFileWriter() {
        evaluationSelectionOfEdgeClientEventLogFileWriter.close()
    }

    private fun neighborWithLargerTransmissionBenefitExists(): Boolean {
        val clientTransmissionBenefit: Float? =
            DataStore.clientTransmissionBenefit.value

        val positionalBeaconFromNtWithLargestTransmissionBenefit: PositionalBeacon? =
            DataStore.measurementManager.getPositionalBeaconFromNtWithLargestTransmissionBenefit()

        // Check if client has largest transmission benefit value of nearby sensing clients
        // --> only makes sense, if the client itself has a value for transmission benefit
        if (!(clientTransmissionBenefit == null || clientTransmissionBenefit == 0.0f)) {
            val neighborWithLargerTransmissionBenefitExists: Boolean =
                if (positionalBeaconFromNtWithLargestTransmissionBenefit == null) {
                    false
                } else if (clientTransmissionBenefit == positionalBeaconFromNtWithLargestTransmissionBenefit.clientTransmissionBenefit) {
                    ByteBuffer.wrap(DataStore.clientId).getInt() < ByteBuffer.wrap(
                        positionalBeaconFromNtWithLargestTransmissionBenefit.clientId.hexToByteArray()
                    ).getInt()
                } else if (clientTransmissionBenefit < positionalBeaconFromNtWithLargestTransmissionBenefit.clientTransmissionBenefit) {
                    true
                } else {
                    false
                }

            return neighborWithLargerTransmissionBenefitExists
        }

        // TODO: Specify further
        // If client has no value for transmission benefit return true, so it won't send to and request data form the server
        return true
    }
}