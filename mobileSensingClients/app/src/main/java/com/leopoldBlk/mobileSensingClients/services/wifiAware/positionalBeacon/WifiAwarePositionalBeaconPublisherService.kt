package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon

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
import androidx.lifecycle.Observer
import com.leopoldBlk.mobileSensingClients.services.wifiAware.WifiAwareService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.callbacks.PublishingPositionalBeaconSessionCallback
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.converter.PositionalBeaconConverter
import crowdDensityLibrary.dataClasses.PositionalBeacon

// Checked (final)
class WifiAwarePositionalBeaconPublisherService : WifiAwareService() {
    private var isWifiAwareSessionInitialized: Boolean = false
    private val wifiAwarePublisherHandlerThread: HandlerThread =
        HandlerThread("WifiAwarePositionalBeaconPublisherHandlerThread")
    private val publishingPositionalBeaconSessionCallback: PublishingPositionalBeaconSessionCallback =
        PublishingPositionalBeaconSessionCallback()

    private lateinit var wifiAwareAttachEventCallbackHandler: Handler
    private lateinit var publishingSessionCallbackHandler: Handler
    private lateinit var publishConfig: PublishConfig

    // Checked (final)
    private val clientPositionalBeaconObserver: Observer<PositionalBeacon?> =
        object : Observer<PositionalBeacon?> {
            override fun onChanged(value: PositionalBeacon?) {
                if (value != null) {
                    updatePublishConfig(value)

                    // wifiAwareSession is already initialized and publishing/updating is possible
                    if (isWifiAwareSessionInitialized) {
                        // A positional beacon has not been published yet
                        if (!publishingPositionalBeaconSessionCallback.publishDiscoverySessionStarted()) {
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
                                wifiAwareSession.publish(
                                    publishConfig,
                                    publishingPositionalBeaconSessionCallback,
                                    publishingSessionCallbackHandler
                                )
                            }
                        }
                        // A positional beacon is already published
                        else {
                            publishingPositionalBeaconSessionCallback.updatePublishConfig(
                                publishConfig
                            )
                        }
                    }
                }
            }
        }

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

        DataStore.clientPositionalBeacon.observe(this, clientPositionalBeaconObserver)
    }

    // Checked (final)
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    // Checked (final)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        wifiAwareManager.attach(wifiAwareAttachEventCallback, wifiAwareAttachEventCallbackHandler)

        return superOnStartCommandStatus
    }

    // Checked (final)
    override fun onDestroy() {
        super.onDestroy()
        isWifiAwareSessionInitialized = false
    }

    // Checked (final)
    private fun updatePublishConfig(updatedPositionalBeacon: PositionalBeacon) {
        val updatedPositionalBeaconAsPositionalBeaconTO: PositionalBeaconTO =
            PositionalBeaconConverter.convertPositionalBeaconToPositionalBeaconTO(
                updatedPositionalBeacon
            )

        val updatedPublishConfig: PublishConfig =
            PublishConfig.Builder()
                .setServiceName("MCS-PositionalBeacon")
                .setServiceSpecificInfo(updatedPositionalBeaconAsPositionalBeaconTO.getPositionalBeaconToAsByteArray())
                .build()

        publishConfig = updatedPublishConfig
    }
}