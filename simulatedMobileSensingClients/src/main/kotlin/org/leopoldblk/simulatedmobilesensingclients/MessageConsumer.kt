package org.leopoldblk.simulatedmobilesensingclients

import crowdDensityLibrary.ForeignAggregatedMeasurementMap
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

// Checked (final)
// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageConsumer {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageConsumer")

    // CoroutineScope for handling received global density data
    private val coroutineScopeHandleReceivedMessages: CoroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Callback method which is invoked when receiving global density data from the server instances.
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

            // Deserialize the received measurement map as this is in JSON format
            val deserializedMeasurementMapFromJson: MeasurementMap =
                MeasurementMapSerializer.deserializeJsonToMeasurementMap(
                    messageBody
                )

            // Adaption of the discretization level of the measurements contained within the FAMM
            val discretizedMeasurementMap: ForeignAggregatedMeasurementMap =
                Utils.adaptionOfDiscretizationLevel(deserializedMeasurementMapFromJson as ForeignAggregatedMeasurementMap)

            // Handover the servers identifier and the received and adapted measurement map to the measurement manager for further processing
            SimulatedMobileSensingClientsApplication.measurementManager.handleReceivedFamm(
                messageProducerId,
                discretizedMeasurementMap
            )

            logger.info("Received global density data from server (${messageProducerId}) in message (${messageId}): ${messageBody}")
        }
    }
}