package com.leopoldBlk.mobileSensingClients

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.gms.time.TrustedTime
import com.google.android.gms.time.TrustedTimeClient
import com.leopoldBlk.mobileSensingClients.services.LocationService
import com.leopoldBlk.mobileSensingClients.services.MessagingService
import com.leopoldBlk.mobileSensingClients.services.PositionalBeaconService
import com.leopoldBlk.mobileSensingClients.services.TransmissionBenefitService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.WifiAwareDensityDataPublisherService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.WifiAwareDensityDataSubscriberService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.WifiAwarePositionalBeaconPublisherService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.WifiAwarePositionalBeaconSubscriberService
import com.leopoldBlk.mobileSensingClients.ui.composables.Map
import com.leopoldBlk.mobileSensingClients.ui.theme.MCSAppTheme
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LogController

// Checked (final)
class MainActivity : ComponentActivity() {
    private val isGeneralFunctionalityEvaluation: Boolean = true
    private val isScalabilityAnalysis: Boolean = false

    private lateinit var trustedTimeClient: TrustedTimeClient

    // Necessary for running H3 Library on Android, Source: https://github.com/isaacbrodsky/h3-android-sample.git
    init {
        System.loadLibrary("h3")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MCSAppTheme {
                Map(false)
            }
        }

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

        LogController.setActiveLogging(false)

        TrustedTime.createClient(applicationContext).addOnSuccessListener { client ->
            trustedTimeClient = client
            DataStore.trustedTimeClient = trustedTimeClient
        }

        if (isGeneralFunctionalityEvaluation || isScalabilityAnalysis) {
            val deviceName: String =
                Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)

            when (deviceName) {
                "Pixel 10 - Device 1" -> DataStore.updateClientId(byteArrayOf(0, 0, 0, 1))
                "Pixel 10 - Device 2" -> DataStore.updateClientId(byteArrayOf(0, 0, 0, 2))
                "Pixel 10 - Device 3" -> DataStore.updateClientId(byteArrayOf(0, 0, 0, 3))
            }

            DataStore.isGeneralFunctionalityEvaluation = isGeneralFunctionalityEvaluation
            DataStore.accuracyThresholdForLocationDeterminationInMeters = 1000.0f
            LogController.setActiveLoggingGeneralFunctionalityEvaluation(isGeneralFunctionalityEvaluation)
        }

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