package org.leopoldblk.simulatedmobilesensingclients

import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

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
    fun onReceiveGlobalDensityData(receivedMessage: String) {
        // Launch coroutine for handling received global density data within the corresponding CoroutineScope
        coroutineScopeHandleReceivedMessages.launch {
            // Split up received message as this is a pair of the id of the server who sent the message and the updated LAMM of that server
            val splitReceivedMessage: List<String> = receivedMessage.removeSurrounding("(", ")").split(", ")
            val serverIdMessageReceivedFrom: String = splitReceivedMessage[0]
            val receivedMeasurementMapAsJson: String = splitReceivedMessage[1]

            // Deserialize the received measurement map as this is in JSON format
            val deserializedMeasurementMapFromJson: MeasurementMap =
                MeasurementMapSerializer.deserializeJsonToMeasurementMap(
                    receivedMeasurementMapAsJson
                )

            // Adaption of the discretization level of the measurements contained within the FAMM
            val discretizedMeasurementMap: ForeignAggregatedMeasurementMap =
                Utils.adaptionOfDiscretizationLevel(deserializedMeasurementMapFromJson as ForeignAggregatedMeasurementMap)

            // Handover the servers identifier and the received and adapted measurement map to the measurement manager for further processing
            SimulatedMobileSensingClientsApplication.measurementManager.handleReceivedFamm(
                serverIdMessageReceivedFrom,
                discretizedMeasurementMap
            )

            logger.info("Received global density data from server ($serverIdMessageReceivedFrom): $receivedMeasurementMapAsJson")
        }
    }
}