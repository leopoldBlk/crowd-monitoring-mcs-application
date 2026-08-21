package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.callbacks

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.ServiceDiscoveryInfo
import android.net.wifi.aware.SubscribeDiscoverySession
import android.util.Log
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.PositionalBeaconTO
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.EdgeClientDecisionController
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.converter.PositionalBeaconConverter
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Checked (final)
class SubscribingPositionalBeaconSessionCallback : DiscoverySessionCallback() {
    private val className: String = "SubscribingPositionalBeaconSessionCallback"

    // CoroutineScope for handling received positional beacon
    private val coroutineScopeHandleReceivedPositionalBeacon: CoroutineScope =
        CoroutineScope(Dispatchers.IO)

//    For testing purpose:
//    private var discoveredServicesCounter: Float = 0.0F
//    private var discoveredServicesPerSecond: Float = 0.0F
//    private lateinit var startingSubscribingTimestamp: LocalDateTime

    // Checked (final)
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

    // Checked (final)
    override fun onSessionConfigFailed() {
        super.onSessionConfigFailed()

        LogController.log(
            Log.ERROR,
            className,
            "Starting subscribe discovery session or updating session config failed!"
        )
    }

    // Checked (final)
    override fun onServiceDiscovered(info: ServiceDiscoveryInfo) {
        // Launch coroutine for handling received positional beacon within the corresponding CoroutineScope
        coroutineScopeHandleReceivedPositionalBeacon.launch {
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

                // Check if client has to take the role of an edge client or has to take of the role of an edge client
                // This might have been changed since a positional beacon containing a larger value
                // for transmission benefit could have been arrived in the previous step
                EdgeClientDecisionController.performEdgeClientDecision()

                LogController.log(
                    Log.INFO,
                    className,
                    "Received positional beacon from client (${receivedPositionalBeacon.clientIdHexString}): ${receivedPositionalBeacon}"
                )
            }
        }
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