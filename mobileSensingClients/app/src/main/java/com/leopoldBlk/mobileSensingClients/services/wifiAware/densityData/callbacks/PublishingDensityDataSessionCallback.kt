package com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.callbacks

import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.util.Log
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import kotlinx.coroutines.CompletableDeferred

// Checked (true)
class PublishingDensityDataSessionCallback : DiscoverySessionCallback() {
    private val className: String = "PublishingDensityDataSessionCallback"
    public val publishDiscoverySessionStarted: CompletableDeferred<Unit> =
        CompletableDeferred<Unit>()

    private lateinit var publishDiscoverySession: PublishDiscoverySession

    // Checked (true)
    override fun onPublishStarted(publishDiscoverySession: PublishDiscoverySession) {
        super.onPublishStarted(publishDiscoverySession)

        LogController.log(
            Log.INFO,
            className,
            "Discovery session for publishing density data started successfully!"
        )

        this.publishDiscoverySession = publishDiscoverySession
        publishDiscoverySessionStarted.complete(Unit)
    }

    // Checked (true)
    override fun onSessionConfigFailed() {
        super.onSessionConfigFailed()

        LogController.log(
            Log.ERROR,
            className,
            "Starting publish discovery session for density data or update of session config failed!"
        )
    }

    // Checked (true)
    override fun onSessionConfigUpdated() {
        super.onSessionConfigUpdated()

        LogController.log(Log.INFO, className, "Session config updated successfully!")
    }

    // Checked (true)
    public fun updatePublishConfig(publishConfig: PublishConfig) {
        publishDiscoverySession.updatePublish(publishConfig)
    }
}