package com.leopoldBlk.mobileSensingClients.services

import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import com.leopoldBlk.mobileSensingClients.BuildConfig
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

class MessagingService : LifecycleService() {
    // Flag to indicate if the client maintains an active connection to the RabbitMQ cluster
    private var isClientConnectedToRabbitMqCluster: Boolean = false

    // Mutex to indicate if there is an ongoing process for establishing connection to the RabbitMQ cluster
    private val runningConnectionEstablishingProcessMutex: Mutex = Mutex(false)

    // Exchange and own queue for receiving global density data
    private val globalDensityDataExchange: String = "rabbit-mq-exchange-global-density-data"
    private val globalDensityDataQueue: String =
        "rabbit-mq-queue-global-density-data-${DataStore.clientIdHexString}"

    // Exchange to publish local density data for
    private val localDensityDataExchange: String = "rabbit-mq-exchange-local-density-data"

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    // CoroutineScope for handling received global density data
    private val coroutineScopeHandleReceivedMessages: CoroutineScope =
        CoroutineScope(Dispatchers.IO)

    // Observer for the transmission benefit of the client
    private val clientHasLargestTransmissionBenefitOfNearbyClientsObserver: Observer<Boolean?> =
        object : Observer<Boolean?> {
            override fun onChanged(value: Boolean?) {
                // If the client has the largest value for transmission benefit consequently
                // representing an edge client and has not already established a connection to the
                // RabbitMQ cluster, the client has to establish a connection in order to receive
                // global and publish local density data
                if (value != null && value && !isClientConnectedToRabbitMqCluster) {
                    coroutineScope.launch {
                        if (!runningConnectionEstablishingProcessMutex.isLocked) {
                            runningConnectionEstablishingProcessMutex.withLock {
                                connectToRabbitMqCluster()
                                connectToGlobalDensityDataExchange()
                            }
                        }
                    }
                }
                // If the client has not the largest value for transmission benefit, but was an edge
                // client previously, the client has to disconnect to the RabbitMQ cluster
                else if (value != null && !value && isClientConnectedToRabbitMqCluster) {
                    // TODO: Maybe find a more elegant solution
                    runBlocking {
                        coroutineScope.launch {
                            disconnectFromGlobalDensityDataExchange()
                            disconnectFromRabbitMqCluster()
                        }
                    }
                }
            }
        }

    private val lammAndOrderForMeasurementTransmissionObserver: Observer<Pair<MeasurementMap, List<String>>?> =
        object : Observer<Pair<MeasurementMap, List<String>>?> {
            override fun onChanged(value: Pair<MeasurementMap, List<String>>?) {
                // Only publish the updated LAMM, if the client is an edge client according to its transmission benefit value AND
                // the LAMM is not empty
                if (value != null && value.first.getAllMeasurements()
                        .isNotEmpty() && isClientConnectedToRabbitMqCluster && DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.value == true
                ) {
                    coroutineScope.launch {
                        // Serialize the updated LAMM according to the order of transmission to JSON format
                        val serializedLammToJson: String =
                            MeasurementMapSerializer.serializeMeasurementMapToJson(
                                value.first,
                                value.second
                            )

                        // Create pair containing own identifier along with the previously updated LAMM
                        val serializedLammToJsonWithClientId: Pair<String, String> =
                            Pair(DataStore.clientIdHexString, serializedLammToJson)

                        channel.basicPublish(
                            localDensityDataExchange,
                            "",
                            null,
                            serializedLammToJsonWithClientId.toString().toByteArray()
                        )
                    }
                }
            }
        }

    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var connection: Connection
    private lateinit var channel: Channel
    private lateinit var globalDensityDataConsumer: Consumer


    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Maybe find a more elegant solution
        runBlocking {
            coroutineScope.launch {
                disconnectFromGlobalDensityDataExchange()
                disconnectFromRabbitMqCluster()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.observe(
            this,
            clientHasLargestTransmissionBenefitOfNearbyClientsObserver
        )

        DataStore.lammAndOrderForMeasurementTransmission.observe(
            this,
            lammAndOrderForMeasurementTransmissionObserver
        )

        if (DataStore.clientHasLargestTransmissionBenefitOfNearbyClients.value == true && !isClientConnectedToRabbitMqCluster) {
            coroutineScope.launch {
                if (!runningConnectionEstablishingProcessMutex.isLocked) {
                    runningConnectionEstablishingProcessMutex.withLock {
                        connectToRabbitMqCluster()
                        connectToGlobalDensityDataExchange()
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun connectToRabbitMqCluster() {
        connectionFactory = ConnectionFactory()

        connectionFactory.host = BuildConfig.RABBIT_MQ_HOST
        connectionFactory.port = BuildConfig.RABBIT_MQ_PORT.toInt()
        connectionFactory.username = BuildConfig.RABBIT_MQ_USERNAME
        connectionFactory.password = BuildConfig.RABBIT_MQ_PASSWORD
        connectionFactory.isAutomaticRecoveryEnabled = true

        while (!isClientConnectedToRabbitMqCluster) {
            try {
                connection = connectionFactory.newConnection()
                channel = connection.createChannel()
                installGlobalDensityDataConsumer(channel)

                isClientConnectedToRabbitMqCluster = true

                LogController.log(
                    Log.INFO,
                    "MessagingService",
                    "Successfully connected to RabbitMQ cluster!"
                )
            } catch (e: Exception) {
                LogController.log(
                    Log.ERROR,
                    "MessagingService",
                    "Not able to establish connection to RabbitMQ cluster!"
                )
            }

            // Retry establishing connection to RabbitMQ Cluster every 5 seconds
            delay(5.seconds)
        }
    }

    private fun connectToGlobalDensityDataExchange() {
        channel.queueDeclare(
            globalDensityDataQueue,
            true,
            false,
            false,
            mapOf<String, Any>(
                Pair<String, String>("x-queue-type", "quorum"),
                Pair<String, Int>("x-expires", 5000)
            )
        )
        LogController.log(
            Log.INFO,
            "MessagingService",
            "Declared globalDensityDataQueue: ${globalDensityDataQueue}"
        )

        channel.queueBind(globalDensityDataQueue, globalDensityDataExchange, "")
        LogController.log(
            Log.INFO,
            "MessagingService",
            "${globalDensityDataQueue} bound to ${globalDensityDataExchange}"
        )

        channel.basicConsume(globalDensityDataQueue, true, globalDensityDataConsumer)
        LogController.log(
            Log.INFO,
            "MessagingService",
            "Started consumer for ${globalDensityDataQueue}"
        )
    }

    private fun installGlobalDensityDataConsumer(channel: Channel) {
        globalDensityDataConsumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                // Launch coroutine for handling received global density data within the corresponding CoroutineScope
                coroutineScopeHandleReceivedMessages.launch {
                    super.handleDelivery(consumerTag, envelope, properties, body)

                    if (body != null) {
                        val messageBodyAsString: String = body.decodeToString()

                        LogController.log(
                            Log.INFO,
                            "MessagingService",
                            "Received global density data: $messageBodyAsString"
                        )

                        // Split up received message as this is pair of the id of the server who sent the message and the updated LAMM of that server
                        val splitReceivedMessage: List<String> =
                            messageBodyAsString.removeSurrounding("(", ")").split(", ")
                        val serverIdMessageReceivedFrom: String = splitReceivedMessage[0]
                        val receivedMeasurementMapAsJson: String = splitReceivedMessage[1]

                        // Deserialize the received measurement map as this is in JSON format
                        val deserializedMeasurementMapFromJson: MeasurementMap =
                            MeasurementMapSerializer.deserializeJsonToMeasurementMap(
                                receivedMeasurementMapAsJson
                            )

                        // Handover the servers identifier and the received density data to the measurement manager for further processing
                        DataStore.measurementManager.handleReceivedFamm(
                            serverIdMessageReceivedFrom,
                            deserializedMeasurementMapFromJson as ForeignAggregatedMeasurementMap
                        )
                    }
                }
            }
        }
    }

    private fun disconnectFromGlobalDensityDataExchange() {
        channel.queueDelete(globalDensityDataQueue)
        LogController.log(
            Log.INFO,
            "MessagingService",
            "Removed globalDensityDataQueue: ${globalDensityDataQueue}"
        )
    }

    private fun disconnectFromRabbitMqCluster() {
        channel.close()
        connection.close()

        isClientConnectedToRabbitMqCluster = false
        LogController.log(Log.INFO, "MessagingService", "Disconnect from RabbitMQ cluster!")
    }
}