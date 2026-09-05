package org.leopoldblk.simulatedmobilesensingclients

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.connection.SimplePropertyValueConnectionNameStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Checked (final)
// Source: https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp
@Configuration
class RabbitMQConfiguration {
    // Name of the queue for receiving the global density data from the servers
    val queueName: String =
        "rabbit-mq-queue-global-density-data-" + SimulatedMobileSensingClientsApplication.clientIdHexString

    // Name of the exchange the servers publish the global density data to
    val exchangeName: String = "rabbit-mq-exchange-global-density-data"

    // Source: https://docs.spring.io/spring-amqp/reference/amqp/connections.html
    @Bean
    fun connectionNameStrategy(): SimplePropertyValueConnectionNameStrategy {
        return SimplePropertyValueConnectionNameStrategy("simulated mobile sensing client ${SimulatedMobileSensingClientsApplication.clientIdHexString}")
    }

    // Creation of the queue for receiving global density data from the server instances
    // Quorum queue enables replication of the queue across the nodes of the RabbitMQ cluster
    @Bean
    fun globalDensityDataQueue(): Queue {
        return QueueBuilder.durable(queueName).quorum().expires(5000).build()
    }

    // Bind queue for receiving global density data to the corresponding exchange
    @Bean
    fun globalDensityDataBinding(): Binding {
        return Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, "", null)
    }
}