package com.leopoldBlk.mobileSensingClients.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.uber.h3core.H3Core
import crowdDensityLibrary.dataClasses.Coordinates
import crowdDensityLibrary.dataClasses.Position
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class LocationService : Service() {
    private val className: String = "LocationService"
    private var h3Core: H3Core = H3Core.newSystemInstance()
    private var locationUpdateExecutor: Executor = Executors.newSingleThreadExecutor()

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var fusedLocationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(p0: Location) {
            LogController.log(
                Log.INFO,
                className,
                "FusedLocationListener - Location update accuracy: ${p0.accuracy}"

            )

            LogController.log(
                Log.DEBUG,
                className,
                "FusedLocationListener - Thread-ID: ${Thread.currentThread().threadId()}"
            )

            // TODO: Choose proper threshold for location accuracy
            if (p0.accuracy < 20.0) {
//                For testing purpose:
//                var updatedClientPositionCellId: String = h3Core.latLngToCellAddress(
//                    48.159728,
//                    11.541551,
//                    13
//                )

                var updatedClientPositionCellId: String = h3Core.latLngToCellAddress(
                    p0.latitude,
                    p0.longitude,
                    13
                )

                // Adding leading zeros to h3 cell id so .hexToByteArray() can be used to convert the string to byte array
                while (updatedClientPositionCellId.length < 16) {
                    updatedClientPositionCellId = "0" + updatedClientPositionCellId
                }

                val updatedClientPosition: Position =
                    Position(Coordinates(p0.latitude, p0.longitude), updatedClientPositionCellId)

                DataStore.clientPosition.postValue(updatedClientPosition)

                LogController.log(
                    Log.INFO,
                    className,
                    "FusedLocationListener - Location updated (Lat/Long): ${p0.latitude}/${p0.longitude}"
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val superOnStartCommandStatus: Int = super.onStartCommand(intent, flags, startId)

        LogController.log(
            Log.DEBUG,
            className,
            "onStartCommand - Thread-ID: ${Thread.currentThread().threadId()}"
        )

        if ((ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED) && (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED)
        ) {
            // TODO: Decide proper priority level for this use case
            // Location updates arrive at most every second (not faster)
            fusedLocationProviderClient.requestLocationUpdates(
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1000L
                ).setMinUpdateIntervalMillis(1000L).build(),
                locationUpdateExecutor,
                fusedLocationListener
            )
        }

        return superOnStartCommandStatus
    }

    override fun onDestroy() {
        super.onDestroy()

        fusedLocationProviderClient.removeLocationUpdates(fusedLocationListener)
    }
}