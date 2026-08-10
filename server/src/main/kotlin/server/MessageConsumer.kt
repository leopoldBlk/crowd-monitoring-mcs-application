package server

import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageConsumer(private val messageProducer: MessageProducer) {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageConsumer")

    // CoroutineScope for handling received global and local density data
    private val coroutineScopeHandleReceivedMessages: CoroutineScope = CoroutineScope(Dispatchers.IO)

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
                logger.info("Received global density data for synchronization purposes from server (${messageProducerId}) in message (${messageId}): ${messageBody}")

                handleReceivedMessage(messageProducerId, messageBody)
            } else {
                logger.info("Received own synchronization message, global density data wont be handled!")
            }
        }
    }

    /**
     * Callback method which handles the local density data sent by the edge clients. As there is one common queue
     * which is used by all edge clients to publish the local density data, the listener is bound to this predefined queue.
     * @param receivedMessage Message arrived at the queue containing local density data.
     */
    @RabbitListener(queues = arrayOf("rabbit-mq-queue-local-density-data"))
    fun onReceiveLocalDensityData(receivedMessage: Message) {
        // Launch coroutine for handling received local density data within the corresponding CoroutineScope
        coroutineScopeHandleReceivedMessages.launch {
            val messageProperties: MessageProperties = receivedMessage.messageProperties
            val messageProducerId: String = messageProperties.getHeader<String>("ProducerId")!!
            val messageId: String = messageProperties.getHeader<String>("MessageId")!!
            val messageBody: String = receivedMessage.body.decodeToString()

            logger.info("Received local density data from client (${messageProducerId}) in message (${messageId}): ${messageBody}")

            // Handle received local density data sent by an edge client
            handleReceivedMessage(messageProducerId, messageBody)

            // TODO: Clarify if this cleanUp step is really necessary here
            ServerApplication.measurementManager.cleanUp()

            // Update own LAMM and order for transmission
            ServerApplication.measurementManager.updateLammAndOrderForTransmission()

            val updatedLammAndOrderForTransmission: Pair<MeasurementMap, List<String>> =
                ServerApplication.measurementManager.getCurrentLammAndOrderForTransmission()

            val lamWithPrioritizedMeasurements: LocalAggregatedMeasurementMap =
                Utils.measurementPrioritization(updatedLammAndOrderForTransmission.first as LocalAggregatedMeasurementMap)

            // Serialize the updated LAMM according to the order of transmission to JSON format
            val serializedLammToJson: String = MeasurementMapSerializer.serializeMeasurementMapToJson(
                lamWithPrioritizedMeasurements,
                updatedLammAndOrderForTransmission.second
            )

            // Publish updated LAMM as message to the exchange for global density data. This way the global density data
            // is transmitted to the edge clients and the other server instances for synchronization purposes.
            messageProducer.publishMessage(
                "rabbit-mq-exchange-global-density-data",
                serializedLammToJson
            )
        }
    }

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
}