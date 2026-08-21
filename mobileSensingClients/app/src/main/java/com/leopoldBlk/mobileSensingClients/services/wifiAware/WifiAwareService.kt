package com.leopoldBlk.mobileSensingClients.services.wifiAware

import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import androidx.lifecycle.LifecycleService

// Checked (final)
abstract class WifiAwareService : LifecycleService() {
    protected lateinit var wifiAwareManager: WifiAwareManager
    protected lateinit var wifiAwareSession: WifiAwareSession

    // Checked (final)
    override fun onCreate() {
        super.onCreate()

        wifiAwareManager =
            applicationContext.getSystemService(WIFI_AWARE_SERVICE) as WifiAwareManager
    }

    // Checked (final)
    override fun onDestroy() {
        super.onDestroy()

        wifiAwareSession.close()
    }
}
