package server

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

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
        rabbitTemplate.convertAndSend(
            exchangeToPublishOn, null,
            messageToBePublished
        )

        logger.info("Published following message: $messageToBePublished")
    }
}