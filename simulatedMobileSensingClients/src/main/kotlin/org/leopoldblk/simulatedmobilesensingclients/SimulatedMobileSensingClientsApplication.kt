package org.leopoldblk.simulatedmobilesensingclients

import crowdDensityLibrary.MeasurementManager
import crowdDensityLibrary.dataClasses.Coordinates
import org.leopoldblk.simulatedmobilesensingclients.Utils.generateRandomCoordinatesWithinGeographicalSquare
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

        // Definition of the coordinates of the top left and the bottom right corner of the geographical square
        // from which the position of the simulated mobile sensing client is randomly chosen (Munich center)
        private val topLeft: Coordinates = Coordinates(48.142977, 11.566536)
        private val bottomRight: Coordinates = Coordinates(48.133292, 11.584525)

        // Randomly generated coordinates of the simulated mobile sensing client
        public val initialCoordinates: Coordinates =
            generateRandomCoordinatesWithinGeographicalSquare(topLeft, bottomRight)

        // Self-generated identifier of the simulated client as hexadecimal string
        public val clientIdHexString: String = clientId.toHexString()

        // Uber H3 cell levels used for discretizing nearby area, discretizing distant area and to determine the nearby
        // area of the client
        public val nearbyAreaDiscretizationLevel: UShort = 12U
        public val distantAreaDiscretizationLevel: UShort = 10U
        public val levelToDetermineNearbyArea: UShort = 11U

        // Measurement manager for handling density data
        public val measurementManager: MeasurementManager = MeasurementManager(clientIdHexString, timeToLiveInSeconds)
    }
}

fun main(args: Array<String>) {
    runApplication<SimulatedMobileSensingClientsApplication>(*args)
}