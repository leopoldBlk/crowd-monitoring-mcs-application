package server

import crowdDensityLibrary.MeasurementManager
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.random.Random

@SpringBootApplication
class ServerApplication {
    companion object {
        // Self-generated identifier of the server
        private val serverId: ByteArray = Random.nextBytes(ByteArray(4))

        // Time to live of the positional beacons and measurements of the crowd monitoring system
        private val timeToLiveInSeconds: ULong = 20u

        // Self-generated identifier of the server as hexadecimal string
        public val serverIdHexString: String = serverId.toHexString()

        // Measurement manager for handling density data (positional beacons & measurement maps)
        public val measurementManager: MeasurementManager = MeasurementManager(serverIdHexString, timeToLiveInSeconds)
    }
}

fun main(args: Array<String>) {
    runApplication<ServerApplication>(*args)
}