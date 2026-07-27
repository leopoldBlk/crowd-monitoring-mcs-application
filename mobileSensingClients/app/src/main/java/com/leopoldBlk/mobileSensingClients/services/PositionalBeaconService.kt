package com.leopoldBlk.mobileSensingClients.services

import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.LogController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon

class PositionalBeaconService : LifecycleService() {
    private val className: String = "PositionalBeaconService"
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val clientPositionObserver: Observer<Position?> =
        object : Observer<Position?> {
            override fun onChanged(value: Position?) {
                coroutineScope.launch {
                    updatePositionalBeacon(value)
                }
            }
        }

    private val clientTransmissionBenefitObserver: Observer<Float?> =
        object : Observer<Float?> {
            override fun onChanged(value: Float?) {
                coroutineScope.launch {
                    updatePositionalBeacon(value)
                }
            }
        }

    private fun updatePositionalBeacon(updatedClientPosition: Position?) {
        val currentClientTransmissionBenefit: Float? = DataStore.clientTransmissionBenefit.value

        if (currentClientTransmissionBenefit != null && updatedClientPosition != null) {

            val updatedPositionalBeacon: PositionalBeacon = PositionalBeacon(
                DataStore.clientIdHexString,
                updatedClientPosition,
                currentClientTransmissionBenefit,
                (System.currentTimeMillis() / 1000).toULong()
            )

            DataStore.clientPositionalBeacon.postValue(updatedPositionalBeacon)
        }

        LogController.log(
            Log.DEBUG,
            className,
            "updatePositionalBeacon - Thread-ID: ${Thread.currentThread().threadId()}"
        )
    }

    private fun updatePositionalBeacon(updatedTransmissionBenefit: Float?) {
        val currentClientPosition: Position? = DataStore.clientPosition.value

        if (currentClientPosition != null && updatedTransmissionBenefit != null) {
            val updatedPositionalBeacon: PositionalBeacon = PositionalBeacon(
                DataStore.clientIdHexString,
                currentClientPosition,
                updatedTransmissionBenefit,
                (System.currentTimeMillis() / 1000).toULong()
            )

            DataStore.clientPositionalBeacon.postValue(updatedPositionalBeacon)
        }

        LogController.log(
            Log.DEBUG,
            className,
            "updatePositionalBeacon - Thread-ID: ${Thread.currentThread().threadId()}"
        )
    }

    override fun onCreate() {
        super.onCreate()

        DataStore.clientPosition.observe(this, clientPositionObserver)
        DataStore.clientTransmissionBenefit.observe(this, clientTransmissionBenefitObserver)

        LogController.log(
            Log.DEBUG,
            className,
            "onCreate - Thread-ID: ${Thread.currentThread().threadId()}"
        )
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }
}