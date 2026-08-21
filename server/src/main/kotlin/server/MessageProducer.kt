package server

import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
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

    init {
        loggerScalabilityAnalysis.info(
            "identifier of the server sent message containing global density data; " +
                    "identifier of sent message containing global density data; " +
                    "timestamp after sending the message containing global density data in milliseconds"
        )
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

        // Generate message properties containing a header with the client and the message identifier
        val messageProperties: MessageProperties = MessageProperties()
        messageProperties.setHeader("ProducerId", ServerApplication.serverIdHexString)
        messageProperties.setHeader("MessageId", messageId.toHexString())

        val message: Message = Message(messageToBePublished.toByteArray(), messageProperties)

        rabbitTemplate.convertAndSend(
            exchangeToPublishOn, null,
            message
        )

        val timestampAfterGlobalDensityDataMessageSent: Long =
            getTimestampIncludingNtpOffset(ServerApplication.ntpSource)
        loggerScalabilityAnalysis.info(
            "${ServerApplication.serverIdHexString}; " +
                    "${messageId.toHexString()}; " +
                    "$timestampAfterGlobalDensityDataMessageSent"
        )

        logger.info("Published following message with the id (${messageId.toHexString()}) as producer with the id (${ServerApplication.serverIdHexString}): ${messageToBePublished}")
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