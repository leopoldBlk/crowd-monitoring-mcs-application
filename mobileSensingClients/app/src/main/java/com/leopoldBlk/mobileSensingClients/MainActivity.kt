package com.leopoldBlk.mobileSensingClients

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leopoldBlk.mobileSensingClients.services.LocationService
import com.leopoldBlk.mobileSensingClients.services.MessagingService
import com.leopoldBlk.mobileSensingClients.services.PositionalBeaconService
import com.leopoldBlk.mobileSensingClients.services.TransmissionBenefitService
import com.leopoldBlk.mobileSensingClients.ui.composables.Map
import com.leopoldBlk.mobileSensingClients.ui.theme.MCSAppTheme
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.WifiAwareDensityDataPublisherService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.WifiAwareDensityDataSubscriberService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.WifiAwarePositionalBeaconPublisherService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.WifiAwarePositionalBeaconSubscriberService

class MainActivity : ComponentActivity() {
    // Necessary for running H3 Library on Android, Source: https://github.com/isaacbrodsky/h3-android-sample.git
    init {
        System.loadLibrary("h3")
    }

    private val isPublisher: Boolean = true
    private val isSubscriber: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MCSAppTheme {
                Map(false)
            }
        }

        LogController.setActiveLogging(true)

        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.INTERNET,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1
        )

        applicationContext.startService(
            Intent(
                applicationContext, LocationService::class.java
            )
        )

        applicationContext.startService(
            Intent(
                applicationContext, TransmissionBenefitService::class.java
            )
        )

        applicationContext.startService(
            Intent(
                applicationContext, PositionalBeaconService::class.java
            )
        )

        applicationContext.startService(
            Intent(
                applicationContext, MessagingService::class.java
            )
        )

        if (isSubscriber) {
            applicationContext.startService(
                Intent(
                    applicationContext, WifiAwarePositionalBeaconSubscriberService::class.java
                )
            )

            applicationContext.startService(
                Intent(
                    applicationContext, WifiAwareDensityDataSubscriberService::class.java
                )
            )
        }

        if (isPublisher) {
            applicationContext.startService(
                Intent(
                    applicationContext, WifiAwarePositionalBeaconPublisherService::class.java
                )
            )

            applicationContext.startService(
                Intent(
                    applicationContext, WifiAwareDensityDataPublisherService::class.java
                )
            )
        }

//        For testing the handling of distant cells:
//        val lammForTest = LocalAggregatedMeasurementMap()
//
//        // Uber H3 level 13 cell
//        val firstMeasurement: Measurement = Measurement(
//            "8d1f8d7a498883f",
//            12u,
//            "01020304",
//            // One hour before timestamp starting the test
//            1000u
//        )
//
//        // Uber H3 level 13 cell
//        val secondMeasurement: Measurement = Measurement(
//            "8d1f8d7a498887f",
//            5u,
//            "01020304",
//            // Two hours before timestamp starting the test
//            2000u
//        )
//
//        // Uber H3 level 13 cell hbf
//        val thirdMeasurement: Measurement = Measurement(
//            "08d1f8d44a48a83f",
//            78u,
//            "01020304",
//            // Two hours before timestamp starting the test
//            3000u
//        )
//
//        // Uber H3 level 13 cell hbf
//        val fourthMeasurement: Measurement = Measurement(
//            "08d1f8d44a48a8ff",
//            94u,
//            "01020304",
//            // Two hours before timestamp starting the test
//            4000u
//        )
//
//        lammForTest.setMeasurement(firstMeasurement)
//        lammForTest.setMeasurement(secondMeasurement)
//        lammForTest.setMeasurement(thirdMeasurement)
//        lammForTest.setMeasurement(fourthMeasurement)
//
//        val lammAfterCellMerge = CellConverter.mergeDistantCells(lammForTest)
    }
}