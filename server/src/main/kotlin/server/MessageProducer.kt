package server

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import kotlin.random.Random

// Sources:
// https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
// https://www.svix.com/guides/rabbitmq/rabbitmq-springboot-tutorial/
@Service
class MessageProducer(private val rabbitTemplate: RabbitTemplate) {
    private val logger: Logger = LoggerFactory.getLogger("RabbitMqMessageProducer")

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

        val message = Message(messageToBePublished.toByteArray(), messageProperties)

        rabbitTemplate.convertAndSend(
            exchangeToPublishOn, null,
            message
        )

        logger.info("Published following message with the id (${messageId.toHexString()}) as producer with the id (${ServerApplication.serverIdHexString}): ${messageToBePublished}")
    }
}