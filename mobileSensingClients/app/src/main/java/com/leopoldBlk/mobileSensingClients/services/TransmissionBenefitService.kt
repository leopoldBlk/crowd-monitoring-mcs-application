package com.leopoldBlk.mobileSensingClients.services

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.controller.TransmissionDecisionController

class TransmissionBenefitService : Service() {
    private val className: String = "TransmissionBenefitService"
    private var clientBatteryLevel: Int? = null
    private var clientConnectionState: Int? = null
    private var networkBatteryUpdateHandlerThread: HandlerThread =
        HandlerThread("NetworkBatteryUpdateHandlerThread")

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkUpdateHandler: Handler
    private lateinit var batteryUpdateHandler: Handler

    private val networkCallback: ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                LogController.log(
                    Log.DEBUG,
                    className,
                    "networkUpdateHandler onAvailable - Thread-ID: ${
                        Thread.currentThread().threadId()
                    }"
                )
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                val hasTransportWifi: Boolean =
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val hasTransportCellular: Boolean =
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                // Connection switched to wifi
                if (hasTransportWifi && clientConnectionState != NetworkCapabilities.TRANSPORT_WIFI) {
                    clientConnectionState = NetworkCapabilities.TRANSPORT_WIFI

                    updateTransmissionBenefit()
                }
                // Connection switched to cellular
                else if (hasTransportCellular && clientConnectionState != NetworkCapabilities.TRANSPORT_CELLULAR) {
                    clientConnectionState = NetworkCapabilities.TRANSPORT_CELLULAR

                    updateTransmissionBenefit()
                }
            }
        }

    val batteryChangeBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {
            batteryUpdateHandler.post {
                if (intent != null) {
                    clientBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)

                    LogController.log(
                        Log.DEBUG,
                        className,
                        "batteryUpdateHandler onReceive - Thread-ID: ${
                            Thread.currentThread().threadId()
                        }"
                    )

                    updateTransmissionBenefit()
                }
            }
        }
    }

    private fun updateTransmissionBenefit() {
        if (clientBatteryLevel != null && clientConnectionState != null) {
            var updatedTransmissionBenefit: Float = 0.0f

            // TODO: Specify proper multiplication factor for Wifi and cellular connection
            if (clientConnectionState == NetworkCapabilities.TRANSPORT_WIFI) {
                updatedTransmissionBenefit = clientBatteryLevel!! * 1.44f
            } else if (clientConnectionState == NetworkCapabilities.TRANSPORT_CELLULAR) {
                updatedTransmissionBenefit = clientBatteryLevel!! * 1.0f
            }

            DataStore.clientTransmissionBenefit.postValue(updatedTransmissionBenefit)

            // Recalculate transmission decision (check if sensing client has the largest transmission benefit of nearby clients)
            // This might have been changed since the value for transmission benefit for this client was updated in the previous step
            TransmissionDecisionController.updateTransmissionDecision()
        }
    }

    override fun onCreate() {
        super.onCreate()

        LogController.log(
            Log.DEBUG,
            className,
            "onCreate - Thread-ID: ${Thread.currentThread().threadId()}"
        )

        networkBatteryUpdateHandlerThread.start()
        networkUpdateHandler = Handler(networkBatteryUpdateHandlerThread.looper)
        batteryUpdateHandler = Handler(networkBatteryUpdateHandlerThread.looper)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_NETWORK_STATE
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            connectivityManager =
                applicationContext.getSystemService(ConnectivityManager::class.java)
            connectivityManager.registerDefaultNetworkCallback(
                networkCallback,
                networkUpdateHandler
            )
        }

        registerReceiver(
            batteryChangeBroadcastReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            RECEIVER_EXPORTED
        )

        return superOnStartCommandStatus
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(batteryChangeBroadcastReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        networkBatteryUpdateHandlerThread.quit()
    }
}