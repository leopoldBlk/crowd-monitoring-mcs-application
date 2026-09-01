package org.leopoldblk.simulatedmobilesensingclients

import com.uber.h3core.H3Core
import crowdDensityLibrary.MeasurementManager
import crowdDensityLibrary.dataClasses.Coordinates
import crowdDensityLibrary.dataClasses.Position
import org.leopoldblk.simulatedmobilesensingclients.Utils.generateRandomCoordinatesWithinGeographicalSquare
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.random.Random

// Checked (final)
@SpringBootApplication
@EnableScheduling
class SimulatedMobileSensingClientsApplication {
    companion object {
        // H3Core instance for performing operations with Uber H3 cells
        private val h3Core: H3Core = H3Core.newInstance()

        // Self-generated identifier of the simulated client
        private val clientId: ByteArray = Random.nextBytes(ByteArray(4))

        // TTL for NT and MMs
        public val timeToLiveNeighborhoodTableInMilliseconds: ULong = 10000u
        public val timeToLiveMeasurementMapInMilliseconds: ULong = 60000u

        // Definition of the coordinates of the top left and the bottom right corner of the geographical square
        // from which the position of the simulated mobile sensing client is randomly chosen (Munich center)
        private val topLeft: Coordinates = Coordinates(48.142977, 11.566536)
        private val bottomRight: Coordinates = Coordinates(48.133292, 11.584525)

        // Self-generated identifier of the simulated client as hexadecimal string
        public val clientIdHexString: String = clientId.toHexString()

        // Uber H3 cell levels used for discretizing nearby area, discretizing distant area and to determine the nearby
        // area of the client
        public val nearbyAreaDiscretizationLevel: UShort = 12U
        public val distantAreaDiscretizationLevel: UShort = 11U
        public val levelToDetermineNearbyArea: UShort = 11U

        // Randomly generated coordinates of the simulated mobile sensing client
        public val initialCoordinates: Coordinates =
            generateRandomCoordinatesWithinGeographicalSquare(topLeft, bottomRight)

        public val initialCellSimulatedClientIsLocatedIn: String = Utils.addLeadingZerosToCellId(
            h3Core.latLngToCellAddress(
                initialCoordinates.latitude,
                initialCoordinates.longitude,
                nearbyAreaDiscretizationLevel.toInt()
            )
        )

        public val simulatedClientPosition: Position =
            Position(initialCoordinates, initialCellSimulatedClientIsLocatedIn)

        // Measurement manager for handling density data
        public val measurementManager: MeasurementManager = MeasurementManager(
            clientIdHexString,
            timeToLiveNeighborhoodTableInMilliseconds,
            timeToLiveMeasurementMapInMilliseconds
        )
    }
}

fun main(args: Array<String>) {
    runApplication<SimulatedMobileSensingClientsApplication>(*args)
}