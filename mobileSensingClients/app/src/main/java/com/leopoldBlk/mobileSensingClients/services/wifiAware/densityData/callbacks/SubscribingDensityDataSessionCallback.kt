package com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.callbacks

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.ServiceDiscoveryInfo
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.DensityDataTO
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.DiscretizationController
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.converter.DensityDataConverter
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

// Checked (final)
class SubscribingDensityDataSessionCallback : DiscoverySessionCallback() {
    private val className: String = "SubscribingDensityDataSessionCallback"
    private val changeOnGoingTransmissionMutex: Mutex = Mutex(false)

    // CoroutineScope for handling received local density data
    private val coroutineScopeHandleReceivedDensityData: CoroutineScope =
        CoroutineScope(Dispatchers.IO)

    // Track ongoing transmission (clientId, TransmissionId, DensityDataTOs of the transmission)
    private lateinit var ongoingTransmission: Triple<String, String, MutableList<DensityDataTO>>

    // Checked (final)
    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
        super.onSubscribeStarted(session)

        LogController.log(
            Log.INFO,
            className,
            "Discovery session for subscribing density data started successfully!"
        )
    }

    // Checked (final)
    override fun onSessionConfigFailed() {
        super.onSessionConfigFailed()

        LogController.log(
            Log.ERROR,
            className,
            "Starting subscribe discovery session for density data failed!"
        )
    }

    // Checked (final)
    override fun onServiceDiscovered(info: ServiceDiscoveryInfo) {
        // Launch coroutine for handling received density data within the corresponding CoroutineScope
        coroutineScopeHandleReceivedDensityData.launch {
            super.onServiceDiscovered(info)

            if (info.serviceSpecificInfo != null) {
                // Convert received byte array to DensityDataTO
                val densityDataTO: DensityDataTO =
                    DensityDataConverter.convertByteArrayToDensityDataTO(info.serviceSpecificInfo as ByteArray)

                val transmissionIdAsHexString: String = densityDataTO.getTransmissionIdAsHexString()
                val clientIdAsHexString: String = densityDataTO.getClientIdAsHexString()

                // If the DensityDataTO belongs to a new transmission and there was no previous ongoing transmission
                if (!::ongoingTransmission.isInitialized) {
                    changeOnGoingTransmissionMutex.withLock {
                        ongoingTransmission =
                            Triple(
                                clientIdAsHexString,
                                transmissionIdAsHexString,
                                mutableListOf(densityDataTO)
                            )
                    }
                }
                // If the DensityDataTO belongs to an already ongoing transmission
                else if (ongoingTransmission.second == transmissionIdAsHexString) {
                    changeOnGoingTransmissionMutex.withLock {
                        ongoingTransmission.third.add(densityDataTO)
                    }
                }
                // If the DensityDataTO belongs to a new transmission and there was a previous ongoing transmission
                // --> Process the buffered DensityDataTOs of the previous ongoing transmission
                // --> Store the DensityDataTO of the new transmission
                else {
                    changeOnGoingTransmissionMutex.withLock {
                        val foreignAggregatedMeasurementMap: ForeignAggregatedMeasurementMap =
                            DensityDataConverter.convertDensityDataTOsToDensityData(
                                ongoingTransmission.third
                            )

                        val discretizedMeasurementMap: ForeignAggregatedMeasurementMap =
                            DiscretizationController.adaptionOfDiscretizationLevel(
                                foreignAggregatedMeasurementMap
                            )

                        DataStore.measurementManager.handleReceivedFamm(
                            ongoingTransmission.first,
                            discretizedMeasurementMap
                        )

                        LogController.logGeneralFunctionalityEvaluation(
                            Log.INFO,
                            "SubscribingDensityDataSessionCallback",
                            "[FR1-C7] [NFR2-C3] Handling received FAMM from client ${ongoingTransmission.first}: ${
                                Json {
                                //    prettyPrint = true
                                }.encodeToString(discretizedMeasurementMap)
                            }"
                        )

                        ongoingTransmission =
                            Triple(
                                clientIdAsHexString,
                                transmissionIdAsHexString,
                                mutableListOf(densityDataTO)
                            )
                    }
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
    }
}