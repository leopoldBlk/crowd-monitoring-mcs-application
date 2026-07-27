package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.callbacks

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.util.Log
import com.leopoldBlk.mobileSensingClients.util.LogController

class PublishingPositionalBeaconSessionCallback : DiscoverySessionCallback() {
    private lateinit var publishDiscoverySession: PublishDiscoverySession

    override fun onPublishStarted(publishDiscoverySession: PublishDiscoverySession) {
        super.onPublishStarted(publishDiscoverySession)

        LogController.log(
            Log.INFO,
            this.javaClass.name,
            "Publish discovery session started successfully!"
        )

        this.publishDiscoverySession = publishDiscoverySession
    }

    override fun onSessionConfigFailed() {
        super.onSessionConfigFailed()

        LogController.log(
            Log.ERROR,
            this.javaClass.name,
            "Starting publish discovery session or updating session config failed!"
        )
    }

    override fun onSessionConfigUpdated() {
        super.onSessionConfigUpdated()

        LogController.log(Log.INFO, this.javaClass.name, "Session config updated successfully!")
    }

    public fun updatePublishConfig(publishConfig: PublishConfig) {
        if (::publishDiscoverySession.isInitialized) {
            publishDiscoverySession.updatePublish(publishConfig)
        }
    }

    public fun publishDiscoverySessionStarted(): Boolean {
        return ::publishDiscoverySession.isInitialized
    }
}