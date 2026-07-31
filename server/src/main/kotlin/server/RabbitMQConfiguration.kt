package server

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Source: https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
@Configuration
class RabbitMQConfiguration {
    // Queue for receiving update to global density data from other server instances
    val queueName: String = "rabbit-mq-queue-global-density-data-" + ServerApplication.serverIdHexString

    // Exchange to publish updates to the global density data
    val exchangeName: String = "rabbit-mq-exchange-global-density-data"

    // Own queue for receiving global density data from other server instances for synchronization purposes
    // Quorum queue enables replication of the queue across the nodes of the RabbitMQ cluster
    @Bean
    fun globalDensityDataQueue(): Queue {
        return QueueBuilder.durable(queueName).quorum().expires(5000).build()
    }

    // Bind queue for receiving global density data updates to corresponding exchange
    @Bean
    fun globalDensityDataBinding(queue: Queue): Binding {
        return Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, "", null)
    }
}