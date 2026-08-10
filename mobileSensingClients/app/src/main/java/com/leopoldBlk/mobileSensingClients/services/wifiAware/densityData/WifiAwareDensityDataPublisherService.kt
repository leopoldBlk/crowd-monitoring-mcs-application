package com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.WifiAwareSession
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.ActivityCompat
import com.leopoldBlk.mobileSensingClients.services.wifiAware.WifiAwareService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.callbacks.PublishingDensityDataSessionCallback
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.DiscretizationController
import com.leopoldBlk.mobileSensingClients.util.controller.LoggingDirectoryController
import com.leopoldBlk.mobileSensingClients.util.controller.TransmissionDecisionController
import com.leopoldBlk.mobileSensingClients.util.converter.DensityDataConverter
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import kotlin.time.Duration.Companion.milliseconds

class WifiAwareDensityDataPublisherService : WifiAwareService() {
    private var isWifiAwareSessionInitialized: Boolean = false
    private val wifiAwarePublisherHandlerThread: HandlerThread =
        HandlerThread("WifiAwareDensityDataPublisherHandlerThread")
    private val publishingDensityDataSessionCallback: PublishingDensityDataSessionCallback =
        PublishingDensityDataSessionCallback()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val evaluationBroadcastedDensityDataLog: File =
        File(LoggingDirectoryController.getEvaluationD2dCommunicationLogFilePath("BroadcastedDensityDataLog"))

    private var lammAsDensityDataTOs: List<DensityDataTO>? = null

    private lateinit var wifiAwareAttachEventCallbackHandler: Handler
    private lateinit var publishingSessionCallbackHandler: Handler
    private lateinit var publishConfigs: MutableList<PublishConfig>
    private lateinit var transmittingLammJob: Job
    private lateinit var evaluationBroadcastedDensityDataLogFileWriter: FileWriter

    private var wifiAwareAttachEventCallback: AttachCallback = object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession?) {
            super.onAttached(session)

            if (session != null) {
                wifiAwareSession = session
                isWifiAwareSessionInitialized = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Create directory for the log file if not already existing
        if (!File(evaluationBroadcastedDensityDataLog.parent).exists()) {
            File(evaluationBroadcastedDensityDataLog.parent).mkdirs()
        }

        // Create file writer which appends new logs to the log file and specifying the logging format
        evaluationBroadcastedDensityDataLogFileWriter =
            FileWriter(evaluationBroadcastedDensityDataLog, true)
        evaluationBroadcastedDensityDataLogFileWriter.write("transmission id; packet id; client id of the broadcaster; number of measurements within the data packet; packet size in bytes (header + measurement map); timestamp within packet header in seconds; timestamp after updating publish config in milliseconds\n")
        evaluationBroadcastedDensityDataLogFileWriter.flush()

        wifiAwarePublisherHandlerThread.start()

        wifiAwareAttachEventCallbackHandler = Handler(wifiAwarePublisherHandlerThread.looper)
        publishingSessionCallbackHandler = Handler(wifiAwarePublisherHandlerThread.looper)

        wifiAwareManager.attach(wifiAwareAttachEventCallback, wifiAwareAttachEventCallbackHandler)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        // TODO: Find more elegant way to repeat transmittingLammJob than while true
        transmittingLammJob = coroutineScope.launch {
            while (true) {
                transmitLamm()
            }
        }

        return superOnStartCommandStatus
    }

    override fun onDestroy() {
        super.onDestroy()

        evaluationBroadcastedDensityDataLogFileWriter.close()
        TransmissionDecisionController.closeEvaluationSelectionOfEdgeClientEventLogFileWriter()

        transmittingLammJob.cancel()
        isWifiAwareSessionInitialized = false
    }

    // Generating/updating the lamm and order for transmission and publish
    private suspend fun transmitLamm() {
        // When not receiving positional beacons or famms from other clients, the nt, lmm and famms will not be updated automatically
        // --> Problem: Old beacons and measurements remain in the clients managed entities and are not removed in the absence of other sensing clients
        // --> Solution: Own cleanup process to get rid of data exceeded the ttl (not defined in concept!)
        // --> Old beacons and measurements are also removed if no positional beacons or famms from other clients are received
        DataStore.measurementManager.cleanUp()

        // Recalculate transmission decision (check if sensing client has the largest transmission benefit of nearby clients)
        // This might have been changed since nt was cleaned in the step before
        TransmissionDecisionController.updateTransmissionDecision()

        // Generate/update the lamm and order for transmission
        val updatedLammAndOrderForTransmission: Pair<MeasurementMap, List<String>> =
            DataStore.measurementManager.updateLammAndOrderForTransmission()

        val lammWithPrioritizedMeasurements: LocalAggregatedMeasurementMap =
            DiscretizationController.measurementPrioritization(updatedLammAndOrderForTransmission.first as LocalAggregatedMeasurementMap)

        val prioritizedLammAndOrderForTransmission =
            Pair(lammWithPrioritizedMeasurements, updatedLammAndOrderForTransmission.second)

        // Post generated/updated lamm so map can update the displayed data
        DataStore.lammAndOrderForMeasurementTransmission.postValue(
            prioritizedLammAndOrderForTransmission
        )

        // Generating the publish configs according to the generated and updated lamm and order of transmission
        updatePublishConfig(prioritizedLammAndOrderForTransmission)

        // TODO: Stop publishing, if the LAMM is now empty but was not previously
        if (isWifiAwareSessionInitialized && publishConfigs.isNotEmpty()) {
            // Split the defined transmission interval for a lamm evenly across the DensityDataTOs to send
            val publishingIntervalForEachConfigInMilliseconds: Long =
                DataStore.publishLammIntervalInMilliseconds.toLong() / publishConfigs.size

            if ((ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) ==
                        PackageManager.PERMISSION_GRANTED) && (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                        PackageManager.PERMISSION_GRANTED)
            ) {
                // First density data published by the client via Wifi Aware
                if (!publishingDensityDataSessionCallback.publishDiscoverySessionStarted.isCompleted) {
                    wifiAwareSession.publish(
                        publishConfigs.first(),
                        publishingDensityDataSessionCallback,
                        publishingSessionCallbackHandler
                    )

                    publishingDensityDataSessionCallback.publishDiscoverySessionStarted.await()

                    val timestampAfterPublishingConfigUpdated: Long =
                        if (DataStore.trustedTimeClient != null) {
                            DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                        } else {
                            System.currentTimeMillis()
                        }

                    evaluationBroadcastedDensityDataLogFileWriter.write(
                        "${lammAsDensityDataTOs!!.first().getTransmissionIdAsHexString()}; " +
                                "${lammAsDensityDataTOs!!.first().getPacketId()}; " +
                                "${lammAsDensityDataTOs!!.first().getClientIdAsHexString()}; " +
                                "${lammAsDensityDataTOs!!.first().getDensityDataLength()}; " +
                                "${
                                    lammAsDensityDataTOs!!.first()
                                        .getDensityDataTOasByteArray().size
                                }; " +
                                "${
                                    lammAsDensityDataTOs!!.first().getHeaderTimestampAsULong()
                                }; " +
                                "${timestampAfterPublishingConfigUpdated}\n"
                    )
                    evaluationBroadcastedDensityDataLogFileWriter.flush()

                    delay(publishingIntervalForEachConfigInMilliseconds.milliseconds)

                    if (publishConfigs.size > 1) {
                        var publishConfigIndexCounter: Int = 1

                        publishConfigs.drop(1).forEach { publishConfig ->
                            publishingDensityDataSessionCallback.updatePublishConfig(
                                publishConfig
                            )

                            val timestampAfterPublishingConfigUpdated: Long =
                                if (DataStore.trustedTimeClient != null) {
                                    DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                                } else {
                                    System.currentTimeMillis()
                                }

                            evaluationBroadcastedDensityDataLogFileWriter.write(
                                "${lammAsDensityDataTOs!![publishConfigIndexCounter].getTransmissionIdAsHexString()}; " +
                                        "${lammAsDensityDataTOs!![publishConfigIndexCounter].getPacketId()}; " +
                                        "${lammAsDensityDataTOs!![publishConfigIndexCounter].getClientIdAsHexString()}; " +
                                        "${lammAsDensityDataTOs!![publishConfigIndexCounter].getDensityDataLength()}; " +
                                        "${
                                            lammAsDensityDataTOs!![publishConfigIndexCounter]
                                                .getDensityDataTOasByteArray().size
                                        }; " +
                                        "${
                                            lammAsDensityDataTOs!![publishConfigIndexCounter].getHeaderTimestampAsULong()
                                        }; " +
                                        "${timestampAfterPublishingConfigUpdated}\n"
                            )
                            evaluationBroadcastedDensityDataLogFileWriter.flush()

                            publishConfigIndexCounter += 1
                            delay(publishingIntervalForEachConfigInMilliseconds.milliseconds)
                        }
                    }
                }
                // Client already published density data via Wifi Aware
                else {
                    var publishConfigIndexCounter: Int = 0

                    publishConfigs.forEach { publishConfig ->
                        publishingDensityDataSessionCallback.updatePublishConfig(
                            publishConfig
                        )

                        val timestampAfterPublishingConfigUpdated: Long =
                            if (DataStore.trustedTimeClient != null) {
                                DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                            } else {
                                System.currentTimeMillis()
                            }

                        evaluationBroadcastedDensityDataLogFileWriter.write(
                            "${lammAsDensityDataTOs!![publishConfigIndexCounter].getTransmissionIdAsHexString()}; " +
                                    "${lammAsDensityDataTOs!![publishConfigIndexCounter].getPacketId()}; " +
                                    "${lammAsDensityDataTOs!![publishConfigIndexCounter].getClientIdAsHexString()}; " +
                                    "${lammAsDensityDataTOs!![publishConfigIndexCounter].getDensityDataLength()}; " +
                                    "${
                                        lammAsDensityDataTOs!![publishConfigIndexCounter]
                                            .getDensityDataTOasByteArray().size
                                    }; " +
                                    "${
                                        lammAsDensityDataTOs!![publishConfigIndexCounter].getHeaderTimestampAsULong()
                                    }; " +
                                    "${timestampAfterPublishingConfigUpdated}\n"
                        )
                        evaluationBroadcastedDensityDataLogFileWriter.flush()

                        publishConfigIndexCounter += 1
                        delay(publishingIntervalForEachConfigInMilliseconds.milliseconds)
                    }
                }
            }
        } else if (publishConfigs.isEmpty()) {
            delay(DataStore.publishLammIntervalInMilliseconds.toLong().milliseconds)
        }
    }

    private fun updatePublishConfig(lammAndOrderForMeasurementTransmission: Pair<MeasurementMap, List<String>>) {
        publishConfigs = mutableListOf<PublishConfig>()

        lammAsDensityDataTOs =
            DensityDataConverter.convertDensityDataToDensityDataTOs(
                lammAndOrderForMeasurementTransmission
            )

        if (lammAsDensityDataTOs != null) {
            for (lammDensityDataTO in lammAsDensityDataTOs) {
                val publishConfigForTO: PublishConfig =
                    PublishConfig.Builder()
                        .setServiceName("MCS-DensityData")
                        .setServiceSpecificInfo(
                            lammDensityDataTO.getDensityDataTOasByteArray()
                        )
                        .build()

                publishConfigs.add(publishConfigForTO)
            }
        }
    }
}