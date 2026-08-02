package org.leopoldblk.simulatedmobilesensingclients

import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageProducer(private val rabbitTemplate: RabbitTemplate) {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageProducer")

    // CoroutineScope for updating and publishing the LAMM
    private val coroutineScopeUpdatePublishLamm: CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Scheduled(fixedRate = 5000)
    fun publishLamm() {
        // Launch coroutine for updating and publishing the LAMM within the corresponding CoroutineScope
        coroutineScopeUpdatePublishLamm.launch {
            // Generate randomized measurements for the nearby area of the sensing client
            val randomMeasurements: List<Measurement> = Utils.generateRandomMeasurementsForNearbyArea(
                SimulatedMobileSensingClientsApplication.simulatedClientPosition,
                SimulatedMobileSensingClientsApplication.nearbyAreaDiscretizationLevel,
                SimulatedMobileSensingClientsApplication.levelToDetermineNearbyArea
            )

            // Use the generated measurements for the nearby area as local measurement map of the sensing client
            SimulatedMobileSensingClientsApplication.measurementManager.overrideLmm(randomMeasurements)

            // Cleanup process to remove entries from the FAMMs which exceeded the ttl
            // TODO: Is cleanup here really necessary?
            SimulatedMobileSensingClientsApplication.measurementManager.cleanUp()

            // Generate/update the LAMM and order for transmission
            val updatedLammAndOrderForTransmission: Pair<MeasurementMap, List<String>> =
                SimulatedMobileSensingClientsApplication.measurementManager.updateLammAndOrderForTransmission()

            // Prioritize the measurements contained within the LAMM
            val lamWithPrioritizedMeasurements: LocalAggregatedMeasurementMap =
                Utils.measurementPrioritization(updatedLammAndOrderForTransmission.first as LocalAggregatedMeasurementMap)

            // Serialize the updated and prioritized LAMM according to the order of transmission to JSON format
            val serializedAndPrioritizedLammToJson: String =
                MeasurementMapSerializer.serializeMeasurementMapToJson(
                    lamWithPrioritizedMeasurements,
                    updatedLammAndOrderForTransmission.second
                )

            // Create pair containing own client identifier along with the previously updated LAMM
            val serializedLammToJsonWithClientId: Pair<String, String> =
                Pair(SimulatedMobileSensingClientsApplication.clientIdHexString, serializedAndPrioritizedLammToJson)

            // Publish the updated LAMM with the client identifier to the exchange for local density data
            publishMessage("rabbit-mq-exchange-local-density-data", serializedLammToJsonWithClientId.toString())
        }
    }

    /**
     * Publishes the given message to the specified exchange.
     * @param exchangeToPublishOn Exchange to send given message to.
     * @param messageToBePublished Message to be published.
     */
    fun publishMessage(exchangeToPublishOn: String, messageToBePublished: String) {
        rabbitTemplate.convertAndSend(
            exchangeToPublishOn, null,
            messageToBePublished
        )

        logger.info("Published following message: $messageToBePublished")
    }
}