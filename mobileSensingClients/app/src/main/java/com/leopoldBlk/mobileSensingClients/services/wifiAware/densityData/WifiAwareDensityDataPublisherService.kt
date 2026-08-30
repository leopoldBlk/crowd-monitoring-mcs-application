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
import android.util.Log
import androidx.core.app.ActivityCompat
import com.leopoldBlk.mobileSensingClients.services.wifiAware.WifiAwareService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.callbacks.PublishingDensityDataSessionCallback
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.DiscretizationController
import com.leopoldBlk.mobileSensingClients.util.controller.EdgeClientDecisionController
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.converter.DensityDataConverter
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class WifiAwareDensityDataPublisherService : WifiAwareService() {
    private var isWifiAwareSessionInitialized: Boolean = false
    private val wifiAwarePublisherHandlerThread: HandlerThread =
        HandlerThread("WifiAwareDensityDataPublisherHandlerThread")
    private val publishingDensityDataSessionCallback: PublishingDensityDataSessionCallback =
        PublishingDensityDataSessionCallback()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var lammAsDensityDataTOs: List<DensityDataTO>? = null
    private val maximumDataPacketsToBroadcastPerPublishingInterval: ULong =
        DataStore.publishLammIntervalInMilliseconds / DataStore.lowerLimitForPublishingIntervalPerDataPacketInMilliseconds

    private lateinit var wifiAwareAttachEventCallbackHandler: Handler
    private lateinit var publishingSessionCallbackHandler: Handler
    private lateinit var publishConfigs: MutableList<PublishConfig>
    private lateinit var transmittingLammJob: Job

    // Checked (final)
    private var wifiAwareAttachEventCallback: AttachCallback = object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession?) {
            super.onAttached(session)

            if (session != null) {
                wifiAwareSession = session
                isWifiAwareSessionInitialized = true
            }
        }
    }

    // Checked (final)
    override fun onCreate() {
        super.onCreate()

        wifiAwarePublisherHandlerThread.start()

        wifiAwareAttachEventCallbackHandler = Handler(wifiAwarePublisherHandlerThread.looper)
        publishingSessionCallbackHandler = Handler(wifiAwarePublisherHandlerThread.looper)

        wifiAwareManager.attach(wifiAwareAttachEventCallback, wifiAwareAttachEventCallbackHandler)
    }

    // Checked (final)
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    // Checked (final)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        transmittingLammJob = coroutineScope.launch {
            while (true) {
                transmitLamm()
            }
        }

        return superOnStartCommandStatus
    }

    // Checked (final)
    override fun onDestroy() {
        super.onDestroy()

        transmittingLammJob.cancel()
        isWifiAwareSessionInitialized = false
    }

    // Checked (final)
    // Generating/updating the lamm and order for transmission and publish
    private suspend fun transmitLamm() {
        val timestampLammAndMessageGenerationsStarts: Long? =
            if (DataStore.trustedTimeClient != null) {
                DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
            } else {
                null
            }

        // When not receiving positional beacons or famms from other clients, the nt, lmm and famms will not be updated automatically
        // --> Problem: Old beacons and measurements remain in the clients managed entities and are not removed in the absence of other sensing clients
        // --> Solution: Own cleanup process to get rid of data exceeded the ttl (not defined in concept!)
        // --> Old beacons and measurements are also removed if no positional beacons or famms from other clients are received
        DataStore.measurementManager.removeExceededBeaconsAndMeasurements()

        // Generate/update the lamm and order for transmission
        val updatedLammAndOrderForTransmission: Pair<MeasurementMap, List<String>> =
            DataStore.measurementManager.updateLammAndOrderForTransmission()

        val lammWithPrioritizedMeasurements: LocalAggregatedMeasurementMap =
            DiscretizationController.measurementPrioritization(updatedLammAndOrderForTransmission.first as LocalAggregatedMeasurementMap)

        val prioritizedLammAndOrderForTransmission =
            Triple(
                lammWithPrioritizedMeasurements,
                updatedLammAndOrderForTransmission.second,
                timestampLammAndMessageGenerationsStarts
            )

        // Post generated/updated lamm so map can update the displayed data
        DataStore.lammAndOrderForMeasurementTransmission.postValue(
            prioritizedLammAndOrderForTransmission
        )

        LogController.logGeneralFunctionalityEvaluation(
            Log.INFO,
            "WifiAwareDensityDataPublisherService",
            "[FR1-C8] [FR3-C1] [FR3-C2] Updated LAMM to the following: ${
                Json {
                    prettyPrint = true
                }.encodeToString(prioritizedLammAndOrderForTransmission.first.getAllMeasurements())
            }"
        )

        // Check if client has to take the role of an edge client
        // This might have been changed since nt was cleaned in the step before
        EdgeClientDecisionController.performEdgeClientDecision()

        // Generating the publish configs according to the generated and updated lamm and order of transmission
        updatePublishConfig(
            Pair(
                prioritizedLammAndOrderForTransmission.first,
                prioritizedLammAndOrderForTransmission.second
            )
        )

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

                    LogController.logGeneralFunctionalityEvaluation(
                        Log.INFO,
                        "WifiAwareDensityDataPublisherService",
                        "[FR1-C7] Updated data packet for LAMM"
                    )

                    publishingDensityDataSessionCallback.publishDiscoverySessionStarted.await()

                    delay(publishingIntervalForEachConfigInMilliseconds.milliseconds)

                    if (publishConfigs.size > 1) {
                        var publishConfigIndexCounter: Int = 1

                        publishConfigs.drop(1).forEach { publishConfig ->
                            publishingDensityDataSessionCallback.updatePublishConfig(
                                publishConfig
                            )

                            LogController.logGeneralFunctionalityEvaluation(
                                Log.INFO,
                                "WifiAwareDensityDataPublisherService",
                                "[FR1-C7] Updated data packet for LAMM"
                            )

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

                        LogController.logGeneralFunctionalityEvaluation(
                            Log.INFO,
                            "WifiAwareDensityDataPublisherService",
                            "[FR1-C7] Updated data packet for LAMM"
                        )

                        publishConfigIndexCounter += 1
                        delay(publishingIntervalForEachConfigInMilliseconds.milliseconds)
                    }
                }
            }
        } else if (publishConfigs.isEmpty() && publishingDensityDataSessionCallback.publishDiscoverySessionStarted.isCompleted) {
            val emptyPublishConfig: PublishConfig =
                PublishConfig.Builder()
                    .setServiceName("MCS-DensityData")
                    .build()

            publishingDensityDataSessionCallback.updatePublishConfig(
                emptyPublishConfig
            )

            delay(DataStore.publishLammIntervalInMilliseconds.toLong().milliseconds)
        } else {
            delay(DataStore.publishLammIntervalInMilliseconds.toLong().milliseconds)
        }
    }

    // Checked (final)
    private fun updatePublishConfig(lammAndOrderForMeasurementTransmission: Pair<MeasurementMap, List<String>>) {
        publishConfigs = mutableListOf<PublishConfig>()

        LogController.logGeneralFunctionalityEvaluation(
            Log.INFO,
            "WifiAwareDensityDataPublisherService",
            "[FR1-C7] Setup PublishConfigs for the following LAMM: ${
                Json {
                    prettyPrint = true
                }.encodeToString(lammAndOrderForMeasurementTransmission.first.getAllMeasurements())
            }"
        )

        LogController.logGeneralFunctionalityEvaluation(
            Log.INFO,
            "WifiAwareDensityDataPublisherService",
            "[FR1-C7] Number of measurements in LAMM to be transmitted: ${lammAndOrderForMeasurementTransmission.first.getAllMeasurements().size}"
        )

        lammAsDensityDataTOs =
            DensityDataConverter.convertDensityDataToDensityDataTOs(
                lammAndOrderForMeasurementTransmission
            )

        if (!lammAsDensityDataTOs.isNullOrEmpty()) {
            LogController.logGeneralFunctionalityEvaluation(
                Log.INFO,
                "WifiAwareDensityDataPublisherService",
                "[FR1-C7] Number of data packets needed to transmit LAMM: ${lammAsDensityDataTOs!!.size}"
            )
        }

        if (lammAsDensityDataTOs != null) {
            var lammDensityDataToCounter: ULong = 0u

            for (lammDensityDataTO in lammAsDensityDataTOs) {
                if (lammDensityDataToCounter == maximumDataPacketsToBroadcastPerPublishingInterval) {
                    break
                }

                val publishConfigForTO: PublishConfig =
                    PublishConfig.Builder()
                        .setServiceName("MCS-DensityData")
                        .setServiceSpecificInfo(
                            lammDensityDataTO.getDensityDataTOasByteArray()
                        )
                        .build()

                if (DataStore.isGeneralFunctionalityEvaluation) {
                    LogController.logGeneralFunctionalityEvaluation(
                        Log.INFO,
                        "WifiAwareDensityDataPublisherService",
                        "[FR1-C7] Created PublishConfig for data packet: ${
                            Json {
                                prettyPrint = true
                            }.encodeToString(lammDensityDataTO)
                        }"
                    )

                    val totalSizeOfLammDensityDataTOInBytes: Int =
                        lammDensityDataTO.getDensityDataTOasByteArray().size

                    LogController.logGeneralFunctionalityEvaluation(
                        Log.INFO,
                        "WifiAwareDensityDataPublisherService",
                        "[FR1-C7] Total size of the data packet in bytes: ${totalSizeOfLammDensityDataTOInBytes}"
                    )

                    if (totalSizeOfLammDensityDataTOInBytes > 255) {
                        LogController.logGeneralFunctionalityEvaluation(
                            Log.ERROR,
                            "WifiAwareDensityDataPublisherService",
                            "[FR1-C7] Total size of the data packet above 255 bytes"
                        )
                    }

                    val amountOfMeasurementsIncludedInLammDensityDataTo: Int =
                        lammDensityDataTO.getDensityData().size

                    LogController.logGeneralFunctionalityEvaluation(
                        Log.INFO,
                        "WifiAwareDensityDataPublisherService",
                        "[FR1-C7] Amount of the measurements contained in the data packet: ${amountOfMeasurementsIncludedInLammDensityDataTo}"
                    )

                    if (amountOfMeasurementsIncludedInLammDensityDataTo > 24) {
                        LogController.logGeneralFunctionalityEvaluation(
                            Log.ERROR,
                            "WifiAwareDensityDataPublisherService",
                            "[FR1-C7] More than 24 measurements contained in data packet"
                        )
                    }

                    var eachMeasurementHasCorrectLength: Boolean = true

                    lammDensityDataTO.getDensityData().forEach { measurementAsByteArray ->
                        if (measurementAsByteArray.size != 10) eachMeasurementHasCorrectLength =
                            false
                    }

                    if (!eachMeasurementHasCorrectLength) {
                        LogController.logGeneralFunctionalityEvaluation(
                            Log.ERROR,
                            "WifiAwareDensityDataPublisherService",
                            "[FR1-C7] One or more measurements do not have the specified length of 10 bytes"
                        )
                    }
                }

                publishConfigs.add(publishConfigForTO)
                lammDensityDataToCounter += 1u
            }
        }
    }
}