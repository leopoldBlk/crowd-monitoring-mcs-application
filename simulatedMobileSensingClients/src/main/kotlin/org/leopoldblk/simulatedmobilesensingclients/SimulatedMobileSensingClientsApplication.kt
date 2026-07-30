package org.leopoldblk.simulatedmobilesensingclients

import crowdDensityLibrary.MeasurementManager
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.random.Random

@SpringBootApplication
@EnableScheduling
class SimulatedMobileSensingClientsApplication {
    companion object {
        // Self-generated identifier of the simulated client
        private val clientId: ByteArray = Random.nextBytes(ByteArray(4))

        // Time to live for the density data of the crowd monitoring system
        private val timeToLiveInSeconds: ULong = 20u

        // Self-generated identifier of the simulated client as hexadecimal string
        public val clientIdHexString: String = clientId.toHexString()

        // Measurement manager for handling density data
        public val measurementManager: MeasurementManager = MeasurementManager(clientIdHexString, timeToLiveInSeconds)
    }
}

fun main(args: Array<String>) {
    runApplication<SimulatedMobileSensingClientsApplication>(*args)
}