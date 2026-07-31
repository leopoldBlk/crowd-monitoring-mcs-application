package org.leopoldblk.simulatedmobilesensingclients

import com.uber.h3core.H3Core
import crowdDensityLibrary.dataClasses.Coordinates
import crowdDensityLibrary.dataClasses.Measurement
import kotlin.random.Random
import kotlin.random.nextUInt

object Utils {
    // H3Core instance for performing operations with Uber H3 cells
    private val h3Core: H3Core = H3Core.newInstance()

    // Generation of random coordinates within a geographical square specified by the top left and bottom right coordinate
    public fun generateRandomCoordinatesWithinGeographicalSquare(
        topLeft: Coordinates,
        bottomRight: Coordinates
    ): Coordinates {
        val randomLatitude: Double = Random.nextDouble(bottomRight.latitude, topLeft.latitude)
        val randomLongitude: Double = Random.nextDouble(topLeft.longitude, bottomRight.longitude)

        return Coordinates(randomLatitude, randomLongitude)
    }

    // Generates a list containing the identifiers of the cells comprising the nearby environment of the client
    // TODO: Add this to the crowdDensityLibrary as this logic is also needed for the android application
    public fun getNearbyAreaForClient(
        clientCellId: String,
        nearbyAreaDiscretizationLevel: UShort,
        levelToDetermineNearbyArea: UShort
    ): List<String> {
        // Get the parent cell the client is located in along with its neighborhood for the level which is used
        // to determine the nearby area of a sensing client
        val nearbyAreaForClientParentCellWithNeighborhood: List<String> =
            getParentCellWithNeighborhood(clientCellId, levelToDetermineNearbyArea)

        val nearbyAreaForClient: MutableList<String> = mutableListOf()

        // Iterate over the parent cell and its neighborhood and add all their child cells at the level
        // used to discretize the nearby area of the sensing client
        for (cellId in nearbyAreaForClientParentCellWithNeighborhood) {
            nearbyAreaForClient.addAll(h3Core.cellToChildren(cellId, nearbyAreaDiscretizationLevel.toInt()))
        }

        return nearbyAreaForClient
    }

    // Generates a list containing the identifier of the parent cell the client is located in along with its neighborhood
    // for the specified level
    // TODO: Add this to the crowdDensityLibrary as this logic is also needed for the android application
    public fun getParentCellWithNeighborhood(
        clientCellId: String,
        levelOfParentCellAndNeighborhood: UShort
    ): List<String> {
        // Get identifier of the parent cell the client is located in for the specified cell level
        val parentCellId: String = h3Core.cellToParentAddress(clientCellId, levelOfParentCellAndNeighborhood.toInt())

        // Get the identifier of the neighboring cells surrounding the parent cell
        val neighborhoodCellIds: List<String> = h3Core.gridRing(parentCellId, 1)

        return listOf<String>(parentCellId).plus(neighborhoodCellIds)
    }

    // Generates a list of random measurements within the nearby area of the sensing client
    public fun generateRandomMeasurementsForNearbyArea(
        clientPosition: Coordinates,
        nearbyAreaDiscretizationLevel: UShort,
        levelToDetermineNearbyArea: UShort
    ): List<Measurement> {
        // Get cell the client is located in
        val cellForClientPosition: String = h3Core.latLngToCellAddress(
            clientPosition.latitude,
            clientPosition.longitude,
            nearbyAreaDiscretizationLevel.toInt()
        )

        // Get the nearby area of the sensing client
        val nearbyAreaForClient: List<String> =
            getNearbyAreaForClient(cellForClientPosition, nearbyAreaDiscretizationLevel, levelToDetermineNearbyArea)

        val randomMeasurements: MutableList<Measurement> = mutableListOf<Measurement>()

        // For each cell within the nearby area of the client generate a random measurement
        for (cellId in nearbyAreaForClient) {

            // The measurement value is randomly chosen between 1 and 20
            val randomMeasurement: Measurement = Measurement(
                cellId,
                Random.nextUInt(UIntRange(1U, 20U)).toUShort(),
                SimulatedMobileSensingClientsApplication.clientIdHexString,
                (System.currentTimeMillis() / 1000).toULong()
            )

            randomMeasurements.add(randomMeasurement)
        }

        return randomMeasurements
    }
}