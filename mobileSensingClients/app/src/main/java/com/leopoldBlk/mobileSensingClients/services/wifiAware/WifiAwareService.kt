package com.leopoldBlk.mobileSensingClients.services.wifiAware

import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import androidx.lifecycle.LifecycleService

abstract class WifiAwareService : LifecycleService() {
    protected lateinit var wifiAwareManager: WifiAwareManager
    protected lateinit var wifiAwareSession: WifiAwareSession

    override fun onCreate() {
        super.onCreate()

        wifiAwareManager =
            applicationContext.getSystemService(WIFI_AWARE_SERVICE) as WifiAwareManager
    }

    override fun onDestroy() {
        super.onDestroy()

        wifiAwareSession.close()
    }
}
