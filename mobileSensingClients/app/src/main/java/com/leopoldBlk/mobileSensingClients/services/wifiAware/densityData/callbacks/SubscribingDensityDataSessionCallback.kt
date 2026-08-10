package com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.callbacks

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.ServiceDiscoveryInfo
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.DensityDataTO
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.DiscretizationController
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.controller.LoggingDirectoryController
import com.leopoldBlk.mobileSensingClients.util.converter.DensityDataConverter
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

class SubscribingDensityDataSessionCallback : DiscoverySessionCallback() {
    private val className: String = "SubscribingDensityDataSessionCallback"

    // CoroutineScope for handling received local density data
    private val coroutineScopeHandleReceivedDensityData: CoroutineScope =
        CoroutineScope(Dispatchers.IO)

    private val evaluationReceivedDensityDataLog: File =
        File(LoggingDirectoryController.getEvaluationD2dCommunicationLogFilePath("ReceivedDensityDataLog"))

    private lateinit var evaluationReceivedDensityDataLogFileWriter: FileWriter

    // Track ongoing transmission (clientId, TransmissionId, DensityDataTOs of the transmission)
    private lateinit var ongoingTransmission: Triple<String, String, MutableList<DensityDataTO>>

    init {
        // Create directory for the log file if not already existing
        if (!File(evaluationReceivedDensityDataLog.parent).exists()) {
            File(evaluationReceivedDensityDataLog.parent).mkdirs()
        }

        // Create file writer which appends new logs to the log file and specifying the logging format
        evaluationReceivedDensityDataLogFileWriter =
            FileWriter(evaluationReceivedDensityDataLog, true)
        evaluationReceivedDensityDataLogFileWriter.write("transmission id; packet id; client id of broadcaster; client id of receiver; number of measurements; packet size in bytes (header + measurement map); timestamp within packet header in seconds; timestamp after discovering the service with the density data in milliseconds\n")
        evaluationReceivedDensityDataLogFileWriter.flush()
    }

    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
        super.onSubscribeStarted(session)

        LogController.log(
            Log.INFO,
            className,
            "Discovery session for subscribing density data started successfully!"
        )
    }

    override fun onSessionConfigFailed() {
        super.onSessionConfigFailed()

        LogController.log(
            Log.ERROR,
            className,
            "Starting subscribe discovery session for density data failed!"
        )
    }

    override fun onServiceDiscovered(info: ServiceDiscoveryInfo) {
        // Launch coroutine for handling received density data within the corresponding CoroutineScope
        coroutineScopeHandleReceivedDensityData.launch {
            val timestampServiceWithDensityDataDiscovered: Long =
                if (DataStore.trustedTimeClient != null) {
                    DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                } else {
                    System.currentTimeMillis()
                }

            super.onServiceDiscovered(info)

            if (info.serviceSpecificInfo != null) {
                // Convert received byte array to DensityDataTO
                val densityDataTO: DensityDataTO =
                    DensityDataConverter.convertByteArrayToDensityDataTO(info.serviceSpecificInfo as ByteArray)

                val transmissionIdAsHexString: String = densityDataTO.getTransmissionIdAsHexString()
                val clientIdAsHexString: String = densityDataTO.getClientIdAsHexString()

                evaluationReceivedDensityDataLogFileWriter.write("${transmissionIdAsHexString}; ${densityDataTO.getPacketId()}; ${clientIdAsHexString}; ${DataStore.clientIdHexString}; ${densityDataTO.getDensityDataLength()}; ${densityDataTO.getDensityDataTOasByteArray().size}; ${densityDataTO.getHeaderTimestampAsULong()}; ${timestampServiceWithDensityDataDiscovered}\n")
                evaluationReceivedDensityDataLogFileWriter.flush()

                // If the DensityDataTO belongs to a new transmission and there was no previous ongoing transmission
                if (!::ongoingTransmission.isInitialized) {
                    ongoingTransmission =
                        Triple(
                            clientIdAsHexString,
                            transmissionIdAsHexString,
                            mutableListOf(densityDataTO)
                        )
                }
                // If the DensityDataTO belongs to an already ongoing transmission
                else if (ongoingTransmission.second == transmissionIdAsHexString) {
                    ongoingTransmission.third.add(densityDataTO)
                }
                // If the DensityDataTO belongs to a new transmission and there was a previous ongoing transmission
                // --> Process the buffered DensityDataTOs of the previous ongoing transmission
                // --> Store the DensityDataTO of the new transmission
                else {
                    val foreignAggregatedMeasurementMap: ForeignAggregatedMeasurementMap =
                        DensityDataConverter.convertDensityDataTOsToDensityData(ongoingTransmission.third)

                    val discretizedMeasurementMap: ForeignAggregatedMeasurementMap =
                        DiscretizationController.adaptionOfDiscretizationLevel(
                            foreignAggregatedMeasurementMap
                        )

                    DataStore.measurementManager.handleReceivedFamm(
                        ongoingTransmission.first,
                        discretizedMeasurementMap
                    )

                    ongoingTransmission =
                        Triple(
                            clientIdAsHexString,
                            transmissionIdAsHexString,
                            mutableListOf(densityDataTO)
                        )
                }

                LogController.log(
                    Log.INFO,
                    className,
                    "Received density data from client $clientIdAsHexString"
                )
            }
        }
    }

    override fun onSessionTerminated() {
        super.onSessionTerminated()

        evaluationReceivedDensityDataLogFileWriter.close()
    }
}