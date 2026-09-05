package server

import crowdDensityLibrary.MeasurementManager
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.random.Random

// Checked (final)
@SpringBootApplication
@EnableScheduling
class ServerApplication {
    companion object {
        // Self-generated identifier of the server
        private val serverId: ByteArray = Random.nextBytes(ByteArray(4))

        // Self-generated identifier of the server as hexadecimal string
        public val serverIdHexString: String = serverId.toHexString()

        // TTL for NT and MMs
        public val timeToLiveNeighborhoodTableInMilliseconds: ULong = 10000u
        public val timeToLiveMeasurementMapInMilliseconds: ULong = 60000u

        // Uber H3 cell levels used for discretizing nearby area, discretizing distant area and to determine the nearby
        // area of the client
        public val nearbyAreaDiscretizationLevel: UShort = 12U
        public val distantAreaDiscretizationLevel: UShort = 11U
        public val levelToDetermineNearbyArea: UShort = 11U

        // NTP server to use for obtaining accurate timestamps for scalability analysis
        // Source: https://developers.google.com/time
        public val ntpSource: String = "time.google.com"

        // Measurement manager for handling density data (measurement maps)
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