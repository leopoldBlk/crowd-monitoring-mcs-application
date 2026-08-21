package com.leopoldBlk.mobileSensingClients.services

import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import com.leopoldBlk.mobileSensingClients.BuildConfig
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.DiscretizationController
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import com.leopoldBlk.mobileSensingClients.util.controller.ScalabilityAnalysisLogController
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Checked (final)
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

    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var connection: Connection
    private lateinit var channel: Channel
    private lateinit var globalDensityDataConsumer: Consumer

    // Checked (final)
    // Observe if the client takes the role as edge client
    private val isEdgeClientObserver: Observer<Boolean?> =
        object : Observer<Boolean?> {
            override fun onChanged(value: Boolean?) {
                // If the client has the largest value for transmission benefit consequently
                // representing an edge client and has not already established a connection to the
                // RabbitMQ cluster, the client has to establish a connection in order to receive
                // global and publish local density data
                if (value != null && value && !isClientConnectedToRabbitMqCluster) {
                    if (!runningConnectionEstablishingProcessMutex.isLocked) {
                        coroutineScope.launch {
                            connectToRabbitMqCluster()
                            connectToGlobalDensityDataExchange()
                        }
                    }
                }
                // If the client has not the largest value for transmission benefit, but was an edge
                // client previously, the client has to disconnect to the RabbitMQ cluster
                else if (value != null && !value && isClientConnectedToRabbitMqCluster) {
                    runBlocking {
                        coroutineScope.launch {
                            disconnectFromGlobalDensityDataExchange()
                            disconnectFromRabbitMqCluster()
                        }
                    }
                }
            }
        }

    // Checked (final)
    private val lammAndOrderForMeasurementTransmissionObserver: Observer<Pair<MeasurementMap, List<String>>?> =
        object : Observer<Pair<MeasurementMap, List<String>>?> {
            override fun onChanged(value: Pair<MeasurementMap, List<String>>?) {
                // Only publish the updated LAMM, if the client is an edge client according to its transmission benefit value AND
                // the LAMM is not empty
                if (value != null && value.first.getAllMeasurements()
                        .isNotEmpty() && isClientConnectedToRabbitMqCluster && DataStore.isEdgeClient.value == true
                ) {
                    coroutineScope.launch {
                        // Serialize the updated LAMM according to the order of transmission to JSON format
                        val serializedLammToJson: String =
                            MeasurementMapSerializer.serializeMeasurementMapToJson(
                                value.first,
                                value.second
                            )

                        // Generate message headers including the identifier of the publisher along
                        // with an identifier for the message which is randomly generated
                        val messageHeaders: MutableMap<String, Any> = mutableMapOf<String, Any>()
                        val messageId: ByteArray = Random.nextBytes(ByteArray(4))
                        messageHeaders["ProducerId"] = DataStore.clientIdHexString
                        messageHeaders["MessageId"] = messageId.toHexString()

                        // Publish the serialized LAMM along with the message headers containing
                        // the producer and message identifier
                        channel.basicPublish(
                            localDensityDataExchange,
                            "",
                            AMQP.BasicProperties.Builder().headers(messageHeaders).build(),
                            serializedLammToJson.toByteArray()
                        )

                        if (DataStore.trustedTimeClient != null) {
                            val timestampAfterLocalDensityDataMessageSent: Long =
                                DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!

                            ScalabilityAnalysisLogController.addScalabilityAnalysisLogSentMessagesEntry(
                                DataStore.clientIdHexString,
                                messageId.toHexString(),
                                timestampAfterLocalDensityDataMessageSent.toULong()
                            )
                        }

                        LogController.log(
                            Log.INFO,
                            "MessagingService",
                            "Published following message with the id (${messageId.toHexString()}) as producer with the id (${DataStore.clientIdHexString}): ${serializedLammToJson}"
                        )
                    }
                }
            }
        }

    // Checked (final)
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        TODO("Return the communication channel to the service.")
    }

    // Checked (final)
    override fun onCreate() {
        super.onCreate()

        ScalabilityAnalysisLogController.setupLogFilesAndWriters()
    }

    // Checked (final)
    override fun onDestroy() {
        super.onDestroy()

        runBlocking {
            if (isClientConnectedToRabbitMqCluster) {
                coroutineScope.launch {
                    disconnectFromGlobalDensityDataExchange()
                    disconnectFromRabbitMqCluster()
                }
            }
        }

        ScalabilityAnalysisLogController.stopScalabilityAnalysisLogging()
    }

    // Checked (final)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DataStore.isEdgeClient.observe(
            this,
            isEdgeClientObserver
        )

        DataStore.lammAndOrderForMeasurementTransmission.observe(
            this,
            lammAndOrderForMeasurementTransmissionObserver
        )

        if (DataStore.isEdgeClient.value == true && !isClientConnectedToRabbitMqCluster) {
            if (!runningConnectionEstablishingProcessMutex.isLocked) {
                coroutineScope.launch {
                    connectToRabbitMqCluster()
                    connectToGlobalDensityDataExchange()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    // Checked (final)
    private suspend fun connectToRabbitMqCluster() {
        runningConnectionEstablishingProcessMutex.withLock {
            connectionFactory = ConnectionFactory()

            connectionFactory.host = BuildConfig.RABBIT_MQ_HOST
            connectionFactory.port = BuildConfig.RABBIT_MQ_PORT.toInt()
            connectionFactory.username = BuildConfig.RABBIT_MQ_USERNAME
            connectionFactory.password = BuildConfig.RABBIT_MQ_PASSWORD
            connectionFactory.isAutomaticRecoveryEnabled = true

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
        }
    }

    // Checked (final)
    private suspend fun connectToGlobalDensityDataExchange() {
        runningConnectionEstablishingProcessMutex.withLock {
            if (isClientConnectedToRabbitMqCluster) {
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
        }
    }

    // Checked (final)
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
                    val timestampMessageProcessingStarts: Long? =
                        if (DataStore.trustedTimeClient != null) {
                            DataStore.trustedTimeClient!!.computeCurrentUnixEpochMillis()!!
                        } else {
                            null
                        }
                    val timemarkMessageProcessingStarts: TimeMark = TimeSource.Monotonic.markNow()

                    super.handleDelivery(consumerTag, envelope, properties, body)

                    if (properties != null && body != null) {
                        val messageProducerId: String = properties.headers["ProducerId"].toString()
                        val messageId: String = properties.headers["MessageId"].toString()
                        val messageBodyAsString: String = body.decodeToString()

                        LogController.log(
                            Log.INFO,
                            "MessagingService",
                            "Received global density data from server (${messageProducerId}) in message (${messageId}): ${messageBodyAsString}"
                        )

                        // Deserialize the received measurement map as this is in JSON format
                        val deserializedMeasurementMapFromJson: MeasurementMap =
                            MeasurementMapSerializer.deserializeJsonToMeasurementMap(
                                messageBodyAsString
                            )

                        val discretizedMeasurementMap: ForeignAggregatedMeasurementMap =
                            DiscretizationController.adaptionOfDiscretizationLevel(
                                deserializedMeasurementMapFromJson as ForeignAggregatedMeasurementMap
                            )

                        // Handover the servers identifier and the received density data to the measurement manager for further processing
                        DataStore.measurementManager.handleReceivedFamm(
                            messageProducerId,
                            discretizedMeasurementMap
                        )

                        val durationForProcessingGlobalDensityDataMessage: Duration =
                            timemarkMessageProcessingStarts.elapsedNow()

                        if (timestampMessageProcessingStarts != null) {
                            ScalabilityAnalysisLogController.addScalabilityAnalysisLogProcessedMessagesEntry(
                                DataStore.clientIdHexString,
                                messageId,
                                timestampMessageProcessingStarts.toULong(),
                                durationForProcessingGlobalDensityDataMessage
                            )
                        }
                    }
                }
            }
        }
    }

    // Checked (final)
    private fun disconnectFromGlobalDensityDataExchange() {
        channel.queueDelete(globalDensityDataQueue)
        LogController.log(
            Log.INFO,
            "MessagingService",
            "Removed globalDensityDataQueue: ${globalDensityDataQueue}"
        )
    }

    // Checked (final)
    private fun disconnectFromRabbitMqCluster() {
        channel.close()
        connection.close()

        isClientConnectedToRabbitMqCluster = false
        LogController.log(Log.INFO, "MessagingService", "Disconnect from RabbitMQ cluster!")
    }
}