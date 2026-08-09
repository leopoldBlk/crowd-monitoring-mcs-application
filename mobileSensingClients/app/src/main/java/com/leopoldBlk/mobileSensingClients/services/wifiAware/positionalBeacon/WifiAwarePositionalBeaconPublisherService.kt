package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.WifiAwareSession
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.leopoldBlk.mobileSensingClients.services.wifiAware.WifiAwareService
import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.callbacks.PublishingPositionalBeaconSessionCallback
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LoggingDirectoryController
import com.leopoldBlk.mobileSensingClients.util.converter.PositionalBeaconConverter
import crowdDensityLibrary.dataClasses.PositionalBeacon
import java.io.File
import java.io.FileWriter

class WifiAwarePositionalBeaconPublisherService : WifiAwareService() {
    private var isWifiAwareSessionInitialized: Boolean = false
    private val wifiAwarePublisherHandlerThread: HandlerThread =
        HandlerThread("WifiAwarePositionalBeaconPublisherHandlerThread")
    private val publishingPositionalBeaconSessionCallback: PublishingPositionalBeaconSessionCallback =
        PublishingPositionalBeaconSessionCallback()
    private val evaluationPublishedPositionalBeaconsLog: File =
        File(LoggingDirectoryController.getEvaluationPositionalBeaconsLogFilePath("PublishedPositionalBeaconsLog"))

    private lateinit var wifiAwareAttachEventCallbackHandler: Handler
    private lateinit var publishingSessionCallbackHandler: Handler
    private lateinit var publishConfig: PublishConfig
    private lateinit var evaluationPublishedPositionalBeaconsLogFileWriter: FileWriter

    private val clientPositionalBeaconObserver: Observer<PositionalBeacon?> =
        object : Observer<PositionalBeacon?> {
            override fun onChanged(value: PositionalBeacon?) {
                if (value != null) {
                    updatePublishConfig(value)

                    // wifiAwareSession is already initialized and publishing/updating is possible
                    if (isWifiAwareSessionInitialized) {
                        // A positional beacon has not been published yet
                        if (!publishingPositionalBeaconSessionCallback.publishDiscoverySessionStarted()) {
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
                                wifiAwareSession.publish(
                                    publishConfig,
                                    publishingPositionalBeaconSessionCallback,
                                    publishingSessionCallbackHandler
                                )
                            }
                        }
                        // A positional beacon is already published
                        else {
                            publishingPositionalBeaconSessionCallback.updatePublishConfig(
                                publishConfig
                            )
                        }

                        val timestampAfterUpdateOfThePublishConfigWithTheNewBeacon: Long = if (DataStore.trustedTimeClient != null){
                            DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                        }
                        else{
                            System.currentTimeMillis()
                        }

                        // Create log entry after the update of the positional beacon with the beacon content and an external timestamp
                        evaluationPublishedPositionalBeaconsLogFileWriter.write("${value.clientId}; ${value.clientPosition.coordinates.latitude}; ${value.clientPosition.coordinates.longitude}; ${value.clientPosition.cellId}; ${value.clientTransmissionBenefit}; ${value.beaconCreationTimestamp}; ${timestampAfterUpdateOfThePublishConfigWithTheNewBeacon}\n")
                        evaluationPublishedPositionalBeaconsLogFileWriter.flush()
                    }
                }
            }
        }

    private var wifiAwareAttachEventCallback: AttachCallback = object : AttachCallback() {
        override fun onAttached(session: WifiAwareSession?) {
            super.onAttached(session)

            if (session != null) {
                wifiAwareSession = session
                isWifiAwareSessionInitialized = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Create directory for the log file if not already existing
        if (!File(evaluationPublishedPositionalBeaconsLog.parent).exists()) {
            File(evaluationPublishedPositionalBeaconsLog.parent).mkdirs()
        }

        // Create file writer which appends new logs to the log file and specifying the logging format
        evaluationPublishedPositionalBeaconsLogFileWriter =
            FileWriter(evaluationPublishedPositionalBeaconsLog, true)
        evaluationPublishedPositionalBeaconsLogFileWriter.write("client id included in the positional beacon; latitude included in the positional beacon; longitude included in the positional beacon; cell id included in the positional beacon; transmission benefit included in the positional beacon; timestamp included in the positional beacon in seconds; timestamp after update of the positional beacon in milliseconds\n")
        evaluationPublishedPositionalBeaconsLogFileWriter.flush()

        wifiAwarePublisherHandlerThread.start()

        wifiAwareAttachEventCallbackHandler = Handler(wifiAwarePublisherHandlerThread.looper)
        publishingSessionCallbackHandler = Handler(wifiAwarePublisherHandlerThread.looper)

        DataStore.clientPositionalBeacon.observe(this, clientPositionalBeaconObserver)
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

        // Close the stream for writing logs
        evaluationPublishedPositionalBeaconsLogFileWriter.close()

        isWifiAwareSessionInitialized = false
    }

    private fun updatePublishConfig(updatedPositionalBeacon: PositionalBeacon) {
        val updatedPositionalBeaconAsPositionalBeaconTO: PositionalBeaconTO =
            PositionalBeaconConverter.convertPositionalBeaconToPositionalBeaconTO(
                updatedPositionalBeacon
            )

        val updatedPublishConfig: PublishConfig =
            PublishConfig.Builder()
                .setServiceName("MCS-PositionalBeacon")
                .setServiceSpecificInfo(updatedPositionalBeaconAsPositionalBeaconTO.getPositionalBeaconToAsByteArray())
                .build()

        publishConfig = updatedPublishConfig
    }
}