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
import com.leopoldBlk.mobileSensingClients.util.controller.EdgeClientDecisionController
import com.leopoldBlk.mobileSensingClients.util.controller.LogController

// Checked (final)
class TransmissionBenefitService : Service() {
    private val className: String = "TransmissionBenefitService"
    private var clientBatteryLevel: Int? = null
    private var clientConnectionState: Int? = null
    private var networkBatteryUpdateHandlerThread: HandlerThread =
        HandlerThread("NetworkBatteryUpdateHandlerThread")

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkUpdateHandler: Handler
    private lateinit var batteryUpdateHandler: Handler

    // Checked (final)
    private val networkCallback: ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                val hasTransportWifi: Boolean =
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val hasTransportCellular: Boolean =
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                // Connection switched to Wifi
                if (hasTransportWifi && clientConnectionState != NetworkCapabilities.TRANSPORT_WIFI) {
                    clientConnectionState = NetworkCapabilities.TRANSPORT_WIFI

                    updateTransmissionBenefit()
                }
                // Connection switched to cellular
                else if (hasTransportCellular && clientConnectionState != NetworkCapabilities.TRANSPORT_CELLULAR) {
                    clientConnectionState = NetworkCapabilities.TRANSPORT_CELLULAR

                    updateTransmissionBenefit()
                }

                LogController.log(
                    Log.INFO,
                    className,
                    "Update in connection state received!"
                )
            }
        }

    // Checked (final)
    private val batteryChangeBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {
            batteryUpdateHandler.post {
                if (intent != null) {
                    clientBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    updateTransmissionBenefit()
                }
            }

            LogController.log(
                Log.INFO,
                className,
                "Update in battery level received!"
            )
        }
    }

    // Checked (final)
    private fun updateTransmissionBenefit() {
        if (clientBatteryLevel != null && clientConnectionState != null) {
            val updatedTransmissionBenefit: Float =
                if (clientConnectionState == NetworkCapabilities.TRANSPORT_WIFI) {
                    clientBatteryLevel!! * DataStore.transmissionBenefitMultiplicationFaktorWifi
                } else if (clientConnectionState == NetworkCapabilities.TRANSPORT_CELLULAR) {
                    clientBatteryLevel!! * DataStore.transmissionBenefitMultiplicationFaktorCellular
                } else {
                    clientBatteryLevel!! * DataStore.transmissionBenefitMultiplicationFaktorNoConnection
                }

            DataStore.clientTransmissionBenefit.postValue(updatedTransmissionBenefit)

            LogController.logGeneralFunctionalityEvaluation(
                Log.INFO,
                className,
                "[FR1-C1] [FR1-C3] Updated transmission benefit to: $updatedTransmissionBenefit (connection state: $clientConnectionState; battery level: $clientBatteryLevel)"
            )

            // Decide if the client has to take the role of an edge client.
            // This might have been changed since the value for transmission benefit for this client was updated in the previous step
            EdgeClientDecisionController.performEdgeClientDecision()
        }
    }

    // Checked (final)
    override fun onCreate() {
        super.onCreate()

        networkBatteryUpdateHandlerThread.start()
        networkUpdateHandler = Handler(networkBatteryUpdateHandlerThread.looper)
        batteryUpdateHandler = Handler(networkBatteryUpdateHandlerThread.looper)
    }

    // Checked (final)
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    // Checked (final)
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

    // Checked (final)
    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(batteryChangeBroadcastReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        networkBatteryUpdateHandlerThread.quit()
    }
}