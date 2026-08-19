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

        // TTL for NT and MMs
        public val timeToLiveNeighborhoodTableInMilliseconds: ULong = 5000u
        public val timeToLiveMeasurementMapInMilliseconds: ULong = 15000u

        // Self-generated identifier of the server as hexadecimal string
        public val serverIdHexString: String = serverId.toHexString()

        // Uber H3 cell levels used for discretizing nearby area, discretizing distant area and to determine the nearby
        // area of the client
        public val nearbyAreaDiscretizationLevel: UShort = 12U
        public val distantAreaDiscretizationLevel: UShort = 11U
        public val levelToDetermineNearbyArea: UShort = 11U

        // Measurement manager for handling density data (positional beacons & measurement maps)
        public val measurementManager: MeasurementManager = MeasurementManager(
            serverIdHexString,
            timeToLiveNeighborhoodTableInMilliseconds,
            timeToLiveMeasurementMapInMilliseconds
        )
    }
}

fun main(args: Array<String>) {
    runApplication<ServerApplication>(*args)
}