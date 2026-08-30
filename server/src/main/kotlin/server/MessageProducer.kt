package server

import crowdDensityLibrary.LocalAggregatedMeasurementMap
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
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.InetAddress
import kotlin.random.Random

// Checked (final)
// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageProducer(private val rabbitTemplate: RabbitTemplate) {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageProducer")
    private val loggerScalabilityAnalysis: Logger =
        LoggerFactory.getLogger("RabbitMqMessageProducer - Scalability Analysis")
    private val ntpClient: NTPUDPClient = NTPUDPClient()

    // CoroutineScope for generating and sending the message containing the LAMM with global density data
    private val coroutineScopeGenerateAndSendLamm: CoroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        loggerScalabilityAnalysis.info(
            "identifier of the server sent the message containing the LAMM with global density data; " +
                    "identifier of sent message containing the LAMM with global density data; " +
                    "timestamp when starting to generate the LAMM and the message containing the LAMM with global density data in milliseconds; " +
                    "timestamp when finishing to generate the LAMM and the message containing the LAMM with global density data in milliseconds; " +
                    "time to to generate the LAMM and the message containing the LAMM with global density data in milliseconds; " +
                    "timestamp after sending the message containing the LAMM with global density data in milliseconds"
        )
    }

    // Checked (final)
    @Scheduled(fixedRate = 5000)
    public fun generateAndSendLamm() {
        coroutineScopeGenerateAndSendLamm.launch {
            val timestampLammAndMessageGenerationsStarts: Long =
                getTimestampIncludingNtpOffset(ServerApplication.ntpSource)

            // Remove exceeded entries from all the FAMMs (additional step)
            ServerApplication.measurementManager.removeExceededBeaconsAndMeasurements()

            // Update own LAMM and order for transmission
            val updatedLammAndOrderForTransmission: Pair<MeasurementMap, List<String>> =
                ServerApplication.measurementManager.updateLammAndOrderForTransmission()

            // Prioritize the measurements with larger discretization level over the ones with lower discretization level
            // which are covering the same area
            val lammWithPrioritizedMeasurements: LocalAggregatedMeasurementMap =
                Utils.measurementPrioritization(updatedLammAndOrderForTransmission.first as LocalAggregatedMeasurementMap)

            // Serialize the updated LAMM according to the order of transmission to JSON format
            val serializedLammToJson: String = MeasurementMapSerializer.serializeMeasurementMapToJson(
                lammWithPrioritizedMeasurements,
                updatedLammAndOrderForTransmission.second
            )

            // Generate a random identifier for the message to be published
            val messageId: ByteArray = Random.nextBytes(ByteArray(4))

            // Generate message properties containing a header with the client and the message identifier
            val messageProperties: MessageProperties = MessageProperties()
            messageProperties.setHeader("ProducerId", ServerApplication.serverIdHexString)
            messageProperties.setHeader("MessageId", messageId.toHexString())

            val message: Message = Message(serializedLammToJson.toByteArray(), messageProperties)

            val timestampLammAndMessageGenerationsEnds: Long =
                getTimestampIncludingNtpOffset(ServerApplication.ntpSource)

            rabbitTemplate.convertAndSend(
                "rabbit-mq-exchange-global-density-data", null,
                message
            )

            val timestampAfterGlobalDensityDataMessageSent: Long =
                getTimestampIncludingNtpOffset(ServerApplication.ntpSource)
            val timeToGenerateLammAndMessage: Long =
                timestampLammAndMessageGenerationsEnds - timestampLammAndMessageGenerationsStarts

            loggerScalabilityAnalysis.info(
                "${ServerApplication.serverIdHexString}; " +
                        "${messageId.toHexString()}; " +
                        "$timestampLammAndMessageGenerationsStarts; " +
                        "$timestampLammAndMessageGenerationsEnds; " +
                        "$timeToGenerateLammAndMessage; " +
                        "$timestampAfterGlobalDensityDataMessageSent"
            )

            logger.info(
                "[FR1-C8] [NFR2-C1] [NFR2-C2] Published message ${messageId.toHexString()} as producer ${ServerApplication.serverIdHexString} to exchange rabbit-mq-exchange-global-density-data " +
                    "containing the LAMM: ${Json { prettyPrint = true }.parseToJsonElement(serializedLammToJson)}"
            )
        }
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