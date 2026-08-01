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
import com.leopoldBlk.mobileSensingClients.util.converter.DensityDataConverter
import com.leopoldBlk.mobileSensingClients.util.controller.TransmissionDecisionController
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import crowdDensityLibrary.MeasurementMap

class WifiAwareDensityDataPublisherService : WifiAwareService() {
    private var isWifiAwareSessionInitialized: Boolean = false
    private val wifiAwarePublisherHandlerThread: HandlerThread =
        HandlerThread("WifiAwareDensityDataPublisherHandlerThread")
    private val publishingDensityDataSessionCallback: PublishingDensityDataSessionCallback =
        PublishingDensityDataSessionCallback()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    // Sources for http client with Ktor:
    // https://ktor.io/docs/client-create-new-application.html
    // https://ktor.io/docs/client-requests.html
    // https://slack-chats.kotlinlang.org/t/16267820/the-documentation-states-that-the-ktor-client-follows-redire
    private val httpClient = HttpClient(CIO) {
        install(HttpRedirect) {
            allowHttpsDowngrade = true
            checkHttpMethod = false
        }
    }

    private lateinit var wifiAwareAttachEventCallbackHandler: Handler
    private lateinit var publishingSessionCallbackHandler: Handler
    private lateinit var publishConfigs: MutableList<PublishConfig>
    private lateinit var transmittingLammJob: Job

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
        val currentLammAndOrderForTransmission: Pair<MeasurementMap, List<String>> =
            DataStore.measurementManager.updateLammAndOrderForTransmission()

        // Post generated/updated lamm so map can update the displayed data
        DataStore.lammAndOrderForMeasurementTransmission.postValue(
            currentLammAndOrderForTransmission
        )

        if (currentLammAndOrderForTransmission != null) {
            // Generating the publish configs according to the generated and updated lamm and order of transmission
            updatePublishConfig(currentLammAndOrderForTransmission)

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

                        delay(publishingIntervalForEachConfigInMilliseconds)

                        if (publishConfigs.size > 1) {
                            publishConfigs.drop(1).forEach { publishConfig ->
                                publishingDensityDataSessionCallback.updatePublishConfig(
                                    publishConfig
                                )
                                delay(publishingIntervalForEachConfigInMilliseconds)
                            }
                        }
                    }
                    // Client already published density data via Wifi Aware
                    else {
                        publishConfigs.forEach { publishConfig ->
                            publishingDensityDataSessionCallback.updatePublishConfig(
                                publishConfig
                            )
                            delay(publishingIntervalForEachConfigInMilliseconds)
                        }
                    }
                }
            }

            // Only send updated lamm to the server, if the client has the largest value for
            // transmission benefit of the nearby clients
//            if (DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.value!!) {
//                val serializedLammToJsonAccordingToTransmissionOrder: String =
//                    MeasurementMapSerializer.serializeMeasurementMapToJson(
//                        currentLammAndOrderForTransmission.first,
//                        currentLammAndOrderForTransmission.second
//                    )
//
//                httpClient.post(BuildConfig.MCS_SERVER_URL + "/${DataStore.clientIdHexString}") {
//                    contentType(ContentType.Text.Plain)
//                    setBody(serializedLammToJsonAccordingToTransmissionOrder)
//                }
//            }
        }
    }

    private fun updatePublishConfig(lammAndOrderForMeasurementTransmission: Pair<MeasurementMap, List<String>>) {
        publishConfigs = mutableListOf<PublishConfig>()

        val lammAsDensityDataTOs: List<DensityDataTO>? =
            DensityDataConverter.convertDensityDataToDensityDataTOs(
                lammAndOrderForMeasurementTransmission
            )

        if (lammAsDensityDataTOs != null) {
            lammAsDensityDataTOs.forEach { lammDensityDataTO ->
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