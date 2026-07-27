package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.WifiAwareSession
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.ActivityCompat
import com.leopoldBlk.mobileSensingClients.services.wifiAware.WifiAwareService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.callbacks.SubscribingPositionalBeaconSessionCallback

class WifiAwarePositionalBeaconSubscriberService : WifiAwareService() {
    private var subscribeConfig: SubscribeConfig =
        SubscribeConfig.Builder().setServiceName("MCS-PositionalBeacon").build()
    private var wifiAwareSubscriberHandlerThread: HandlerThread =
        HandlerThread("WifiAwarePositionalBeaconSubscriberHandlerThread")

    lateinit var wifiAwareAttachEventCallbackHandler: Handler
    lateinit var subscribingSessionCallbackHandler: Handler

    private var wifiAwareAttachEventCallback: AttachCallback = object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession?) {
            super.onAttached(session)

            if (session != null) {
                wifiAwareSession = session

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
                    wifiAwareSession.subscribe(
                        subscribeConfig,
                        SubscribingPositionalBeaconSessionCallback(),
                        subscribingSessionCallbackHandler
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        wifiAwareSubscriberHandlerThread.start()

        wifiAwareAttachEventCallbackHandler = Handler(wifiAwareSubscriberHandlerThread.looper)
        subscribingSessionCallbackHandler = Handler(wifiAwareSubscriberHandlerThread.looper)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        wifiAwareManager.attach(wifiAwareAttachEventCallback, wifiAwareAttachEventCallbackHandler)

        return superOnStartCommandStatus
    }

    override fun onDestroy() {
        super.onDestroy()

        wifiAwareSubscriberHandlerThread.quit()
    }
}