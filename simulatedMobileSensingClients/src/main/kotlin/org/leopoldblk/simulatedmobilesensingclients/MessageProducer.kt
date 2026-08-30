package org.leopoldblk.simulatedmobilesensingclients

import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.random.Random

// Checked (final)
// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageProducer(private val rabbitTemplate: RabbitTemplate) {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageProducer")

    // CoroutineScope for updating and publishing the LAMM
    private val coroutineScopeUpdateAndPublishLamm: CoroutineScope = CoroutineScope(Dispatchers.IO)

    // Checked (final)
    @Scheduled(fixedRate = 30000)
    fun updateLmm() {
        // Launch coroutine for updating the LMM within the corresponding CoroutineScope
        coroutineScopeUpdateAndPublishLamm.launch {
            // Generate randomized measurements for the nearby area of the sensing client
            val randomMeasurements: List<Measurement> = Utils.generateRandomMeasurementsForNearbyArea(
                SimulatedMobileSensingClientsApplication.simulatedClientPosition,
                SimulatedMobileSensingClientsApplication.nearbyAreaDiscretizationLevel,
                SimulatedMobileSensingClientsApplication.levelToDetermineNearbyArea
            )

            // Use the generated measurements for the nearby area as local measurement map of the sensing client
            SimulatedMobileSensingClientsApplication.measurementManager.clearLmmAndSetNewMeasurements(randomMeasurements)

            logger.info(
                "[FR2] Updated LAMM to: ${
                    Json { prettyPrint = true }.encodeToString(
                        SimulatedMobileSensingClientsApplication.measurementManager.getCurrentLammAndOrderForTransmission().first
                    )
                }"
            )
        }
    }

    // Checked (final)
    @Scheduled(fixedRate = 5000)
    fun publishLamm() {
        // Launch coroutine for updating and publishing the LAMM within the corresponding CoroutineScope
        coroutineScopeUpdateAndPublishLamm.launch {
            // Cleanup process to remove entries from the FAMMs which exceeded the ttl (additional step)
            SimulatedMobileSensingClientsApplication.measurementManager.removeExceededBeaconsAndMeasurements()

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

            // Publish the updated LAMM to the exchange for local density data
            publishMessage("rabbit-mq-exchange-local-density-data", serializedAndPrioritizedLammToJson)
        }
    }

    // Checked (final)
    /**
     * Publishes the given message to the specified exchange.
     * @param exchangeToPublishOn Exchange to send given message to.
     * @param messageToBePublished Message to be published.
     */
    fun publishMessage(exchangeToPublishOn: String, messageToBePublished: String) {
        // Generate a random identifier for the message to be published
        val messageId: ByteArray = Random.nextBytes(ByteArray(4))

        // Generate message properties containing a header with client and the message identifier
        val messageProperties: MessageProperties = MessageProperties()
        messageProperties.setHeader("ProducerId", SimulatedMobileSensingClientsApplication.clientIdHexString)
        messageProperties.setHeader("MessageId", messageId.toHexString())

        val message = Message(messageToBePublished.toByteArray(), messageProperties)

        rabbitTemplate.convertAndSend(
            exchangeToPublishOn, null,
            message
        )

        logger.info("Published following message with the id (${messageId.toHexString()}) as producer with the id (${SimulatedMobileSensingClientsApplication.clientIdHexString}): ${messageToBePublished}")
    }
}