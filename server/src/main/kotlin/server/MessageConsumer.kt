package server

import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service
import java.net.InetAddress

// Checked (final)
// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageConsumer(private val globalDensityDataQueue: Queue) {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageConsumer")
    private val loggerScalabilityAnalysis: Logger =
        LoggerFactory.getLogger("RabbitMqMessageConsumer - Scalability Analysis")
    private val ntpClient: NTPUDPClient = NTPUDPClient()

    // CoroutineScope for handling received global and local density data
    private val coroutineScopeHandleReceivedMessages: CoroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        loggerScalabilityAnalysis.info(
            "identifier of the server processing the message containing a FAMM with local density data; " +
                    "identifier of processed message containing a FAMM with local density data; " +
                    "timestamp when starting to process the message containing a FAMM with local density data in milliseconds; " +
                    "timestamp when finishing to process the message containing a FAMM with local density data in milliseconds; " +
                    "duration the message containing a FAMM with local density data took to process in milliseconds"
        )
    }

    // Checked (final)
    /**
     * Callback method which handles the local density data sent by the edge clients. As there is one common queue
     * which is used by all edge clients to publish the local density data, the listener is bound to this predefined queue.
     * @param receivedMessage Message arrived at the queue containing local density data.
     */
    @RabbitListener(queues = arrayOf("rabbit-mq-queue-local-density-data"))
    fun onReceiveLocalDensityData(receivedMessage: Message) {
        // Launch coroutine for handling received local density data within the corresponding CoroutineScope
        coroutineScopeHandleReceivedMessages.launch {
            val timestampMessageProcessingStarts: Long = getTimestampIncludingNtpOffset(ServerApplication.ntpSource)

            val messageProperties: MessageProperties = receivedMessage.messageProperties
            val messageProducerId: String = messageProperties.getHeader<String>("ProducerId")!!
            val messageId: String = messageProperties.getHeader<String>("MessageId")!!
            val messageBody: String = receivedMessage.body.decodeToString()

            // Handle received local density data sent by an edge client
            handleReceivedMessage(messageProducerId, messageBody)

            val timestampMessageProcessingEnds: Long = getTimestampIncludingNtpOffset(ServerApplication.ntpSource)
            val durationForProcessingLocalDensityDataMessage: Long =
                timestampMessageProcessingEnds - timestampMessageProcessingStarts

            loggerScalabilityAnalysis.info(
                "${ServerApplication.serverIdHexString}; " +
                        "$messageId; " +
                        "$timestampMessageProcessingStarts; " +
                        "$timestampMessageProcessingEnds; " +
                        "$durationForProcessingLocalDensityDataMessage"
            )

            logger.info(
                "[FR1-C8] [NFR2-C1] Received message $messageId from producer $messageProducerId from queue rabbit-mq-queue-local-density-data " +
                    "containing the FAMM: ${Json { prettyPrint = true }.parseToJsonElement(messageBody)}"
            )
        }
    }

    // Checked (final)
    /**
     * Callback method which handles the global density data sent by other server instances for synchronization purpose.
     * The name of the queue to register the listener is specified using Spring Expression Language. This is because
     * the queue name is not known at compile time, because it is constructed at runtime.
     * @param receivedMessage Message arrived at the queue containing global density data.
     */
    @RabbitListener(queues = ["#{globalDensityDataQueue.getName()}"])
    fun onReceiveGlobalDensityData(receivedMessage: Message) {
        // Launch coroutine for handling received global density data within the corresponding CoroutineScope
        coroutineScopeHandleReceivedMessages.launch {
            val messageProperties: MessageProperties = receivedMessage.messageProperties
            val messageProducerId: String = messageProperties.getHeader<String>("ProducerId")!!
            val messageId: String = messageProperties.getHeader<String>("MessageId")!!
            val messageBody: String = receivedMessage.body.decodeToString()

            // Only handle received global density data sent by other server instances
            if (messageProducerId != ServerApplication.serverIdHexString) {
                logger.info(
                    "[FR1-C8] [NFR2-C1] Received message $messageId from producer $messageProducerId from queue ${globalDensityDataQueue.name} " +
                        "containing the FAMM: ${Json { prettyPrint = true }.parseToJsonElement(messageBody)}"
                )

                handleReceivedMessage(messageProducerId, messageBody)
            }
        }
    }

    // Checked (final)
    /**
     * Handling of a measurement map sent by an edge client or another server instance.
     * @param senderMessageReceivedFrom Identifier of the measurement maps sender. This might be an edge client or another server instance.
     * @param receivedMeasurementMapAsJson Received measurement map in JSON format.
     */
    private suspend fun handleReceivedMessage(senderMessageReceivedFrom: String, receivedMeasurementMapAsJson: String) {
        // Deserialize the received measurement map as this is in JSON format
        val deserializedMeasurementMapFromJson: MeasurementMap =
            MeasurementMapSerializer.deserializeJsonToMeasurementMap(receivedMeasurementMapAsJson)

        // Handover the senders identifier and the received density data to the measurement manager for further processing
        ServerApplication.measurementManager.handleReceivedFamm(
            senderMessageReceivedFrom,
            deserializedMeasurementMapFromJson as ForeignAggregatedMeasurementMap
        )
    }

    // Checked (final)
    /**
     * Returns an accurate timestamp according to the time provided by a NTP server.
     * @param timeSource Address of the NTP server to use for obtaining accurate time.
     */
    private fun getTimestampIncludingNtpOffset(timeSource: String): Long {
        ntpClient.open()

        val timeInfo: TimeInfo = ntpClient.getTime(InetAddress.getByName(timeSource))
        timeInfo.computeDetails()
        val timestampIncludingNtpOffset: Long = System.currentTimeMillis() + timeInfo.offset

        ntpClient.close()
        return timestampIncludingNtpOffset
    }
}