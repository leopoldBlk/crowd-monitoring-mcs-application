package com.leopoldBlk.mobileSensingClients.services

import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

// Checked (final)
class PositionalBeaconService : LifecycleService() {
    private val className: String = "PositionalBeaconService"
    private val coroutineScopeUpdatePositionalBeacon: CoroutineScope =
        CoroutineScope(Dispatchers.Default)

    // Checked (final)
    override fun onCreate() {
        super.onCreate()
    }

    // Checked (final)
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    // Checked (final)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        coroutineScopeUpdatePositionalBeacon.launch {
            while (true) {
                val updatedPostionalBeacon: PositionalBeacon? = updatePositionalBeacon()

                if (updatedPostionalBeacon != null) {
                    DataStore.measurementManager.handleReceivedPositionalBeacon(
                        updatedPostionalBeacon
                    )
                }

                delay(DataStore.publishPositionalBeaconIntervalInMilliseconds.toLong().milliseconds)
            }
        }

        return superOnStartCommandStatus
    }

    // Checked (final)
    override fun onDestroy() {
        super.onDestroy()

        coroutineScopeUpdatePositionalBeacon.cancel()
    }

    // Checked (final)
    private fun updatePositionalBeacon(): PositionalBeacon? {
        val currentClientPosition: Position? = DataStore.clientPosition.value
        val currentClientTransmissionBenefit: Float? = DataStore.clientTransmissionBenefit.value

        if (currentClientPosition != null && currentClientTransmissionBenefit != null) {
            val updatedPositionalBeacon: PositionalBeacon = PositionalBeacon(
                DataStore.clientIdHexString,
                currentClientPosition,
                currentClientTransmissionBenefit,
                (System.currentTimeMillis() / 1000).toULong()
            )

            DataStore.clientPositionalBeacon.postValue(updatedPositionalBeacon)

            LogController.log(
                Log.INFO,
                className,
                "Updated positional beacon: ${
                    Json { prettyPrint = true }.encodeToString(
                        updatedPositionalBeacon
                    )
                }",
            )

            return updatedPositionalBeacon
        } else if (currentClientPosition == null) {
            LogController.log(
                Log.INFO,
                className,
                "Positional beacon was not updated, due to currentClientPosition is null!"
            )
        } else {
            LogController.log(
                Log.INFO,
                className,
                "Positional beacon was not updated, due to currentClientTransmissionBenefit is null!"
            )
        }

        return null
    }
}