package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.callbacks

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.ServiceDiscoveryInfo
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.PositionalBeaconTO
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.controller.LoggingDirectoryController
import com.leopoldBlk.mobileSensingClients.util.controller.TransmissionDecisionController
import com.leopoldBlk.mobileSensingClients.util.converter.PositionalBeaconConverter
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

class SubscribingPositionalBeaconSessionCallback : DiscoverySessionCallback() {
    private val className: String = "SubscribingPositionalBeaconSessionCallback"

    // CoroutineScope for handling received positional beacon
    private val coroutineScopeHandleReceivedPositionalBeacon: CoroutineScope =
        CoroutineScope(Dispatchers.IO)

    private val evaluationReceivedPositionalBeaconsLog: File =
        File(LoggingDirectoryController.getEvaluationD2dCommunicationLogFilePath("ReceivedPositionalBeaconsLog"))

    private lateinit var evaluationReceivedPositionalBeaconsLogFileWriter: FileWriter

    init {
        // Create directory for the log file if not already existing
        if (!File(evaluationReceivedPositionalBeaconsLog.parent).exists()) {
            File(evaluationReceivedPositionalBeaconsLog.parent).mkdirs()
        }

        // Create file writer which appends new logs to the log file and specifying the logging format
        evaluationReceivedPositionalBeaconsLogFileWriter =
            FileWriter(evaluationReceivedPositionalBeaconsLog, true)
        evaluationReceivedPositionalBeaconsLogFileWriter.write("client id included in the positional beacon; latitude included in the positional beacon; longitude included in the positional beacon; cell id included in the positional beacon; transmission benefit included in the positional beacon; timestamp included in the positional beacon in seconds; timestamp after discovering the service with the positional beacon in milliseconds\n")
        evaluationReceivedPositionalBeaconsLogFileWriter.flush()
    }

//    For testing purpose:
//    private var discoveredServicesCounter: Float = 0.0F
//    private var discoveredServicesPerSecond: Float = 0.0F
//    private lateinit var startingSubscribingTimestamp: LocalDateTime

    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
        super.onSubscribeStarted(session)

//        For testing purpose:
//        startingSubscribingTimestamp = LocalDateTime.now()

        LogController.log(
            Log.INFO,
            className,
            "Subscribe discovery session started successfully!"
        )
    }

    override fun onSessionConfigFailed() {
        super.onSessionConfigFailed()

        LogController.log(
            Log.ERROR,
            className,
            "Starting subscribe discovery session or updating session config failed!"
        )
    }

    override fun onServiceDiscovered(info: ServiceDiscoveryInfo) {
        // Launch coroutine for handling received positional beacon within the corresponding CoroutineScope
        coroutineScopeHandleReceivedPositionalBeacon.launch {
            val timestampServiceWithPositionalBeaconDiscovered: Long =
                if (DataStore.trustedTimeClient != null) {
                    DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                } else {
                    System.currentTimeMillis()
                }

            super.onServiceDiscovered(info)
//        For testing purpose:
//        calculateDiscoveredServicesPerSecond()

            if (info.serviceSpecificInfo != null) {
                val receivedPositionalBeaconTO: PositionalBeaconTO =
                    PositionalBeaconConverter.convertByteArrayToPositionalBeaconTO(info.serviceSpecificInfo as ByteArray)
                val receivedPositionalBeacon: PositionalBeacon =
                    PositionalBeaconConverter.convertPositionalBeaconToToPositionalBeacon(
                        receivedPositionalBeaconTO
                    )

                DataStore.measurementManager.handleReceivedPositionalBeacon(receivedPositionalBeacon)

                // Recalculate transmission decision (check if sensing client has the largest transmission benefit of nearby clients)
                // This might have been changed since a positional beacon containing a larger value
                // for transmission benefit could have been arrived in the previous step
                TransmissionDecisionController.updateTransmissionDecision()

                evaluationReceivedPositionalBeaconsLogFileWriter.write("${receivedPositionalBeacon.clientIdHexString}; ${receivedPositionalBeacon.clientPosition.coordinates.latitude}; ${receivedPositionalBeacon.clientPosition.coordinates.longitude}; ${receivedPositionalBeacon.clientPosition.cellIdHexString}; ${receivedPositionalBeacon.clientTransmissionBenefit}; ${receivedPositionalBeacon.beaconCreationTimestamp}; ${timestampServiceWithPositionalBeaconDiscovered}\n")
                evaluationReceivedPositionalBeaconsLogFileWriter.flush()

                LogController.log(
                    Log.INFO,
                    className,
                    "Received positional beacon from client (${receivedPositionalBeacon.clientIdHexString}): ${receivedPositionalBeacon}"
                )
            }
        }
    }

    override fun onSessionTerminated() {
        super.onSessionTerminated()

        evaluationReceivedPositionalBeaconsLogFileWriter.close()
        TransmissionDecisionController.closeEvaluationSelectionOfEdgeClientEventLogFileWriter()
    }

//    For testing purpose:
//    Avg. service discoveries per second: ca. 3.4303482
//    private fun calculateDiscoveredServicesPerSecond() {
//        val serviceDiscoveredTimestamp: LocalDateTime = LocalDateTime.now()
//        val timedelta: Duration =
//            Duration.between(startingSubscribingTimestamp, serviceDiscoveredTimestamp)
//
//        discoveredServicesCounter += 1
//
//        if (timedelta.seconds > 0) {
//            discoveredServicesPerSecond = discoveredServicesCounter / timedelta.seconds
//            LogController.log(
//                Log.INFO,
//                this.javaClass.name,
//                "Avg. service discoveries per second: " + discoveredServicesPerSecond
//            )
//        }
//    }
}