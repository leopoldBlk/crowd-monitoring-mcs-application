package org.leopoldblk.simulatedmobilesensingclients

import com.uber.h3core.H3Core
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.dataClasses.Coordinates
import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.dataClasses.Position
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.random.nextUInt

// Checked (final)
object Utils {
    private val logger: Logger = LoggerFactory.getLogger("Utils")

    // H3Core instance for performing operations with Uber H3 cells
    private val h3Core: H3Core = H3Core.newInstance()

    // Checked (final)
    // Adds leading zeros to cell identifier string, as Uber H3 cuts leading zeros off when generating
    // cell identifiers
    public fun addLeadingZerosToCellId(cellId: String): String {
        var cellIdWithLeadingZeros = cellId

        while (cellIdWithLeadingZeros.length < 16) {
            cellIdWithLeadingZeros = "0" + cellIdWithLeadingZeros
        }

        return cellIdWithLeadingZeros
    }

    // Checked (final)
    // Generation of random coordinates within a geographical square specified by the top left and bottom right coordinate
    public fun generateRandomCoordinatesWithinGeographicalSquare(
        topLeft: Coordinates,
        bottomRight: Coordinates
    ): Coordinates {
        val randomLatitude: Double = Random.nextDouble(bottomRight.latitude, topLeft.latitude)
        val randomLongitude: Double = Random.nextDouble(topLeft.longitude, bottomRight.longitude)

        return Coordinates(randomLatitude, randomLongitude)
    }

    // Checked (final)
    // Generates a list of random measurements within the nearby area of the sensing client for the cell level used
    // to discretize nearby environments
    public fun generateRandomMeasurementsForNearbyArea(
        clientPosition: Position,
        nearbyAreaDiscretizationLevel: UShort,
        levelToDetermineNearbyArea: UShort
    ): List<Measurement> {
        // Get the nearby area of the sensing client for the cell level used to discretize nearby environments
        val nearbyAreaForClient: List<String> =
            getNearbyAreaForClient(
                clientPosition.cellIdHexString,
                nearbyAreaDiscretizationLevel,
                levelToDetermineNearbyArea
            )

        val randomMeasurements: MutableList<Measurement> = mutableListOf<Measurement>()

        // For each cell within the nearby area of the client generate a random measurement
        for (cellId in nearbyAreaForClient) {
            // The measurement value is randomly chosen between 1 and 20
            val randomMeasurement: Measurement = Measurement(
                addLeadingZerosToCellId(cellId),
                Random.nextUInt(UIntRange(1U, 20U)).toUShort(),
                SimulatedMobileSensingClientsApplication.clientIdHexString,
                (System.currentTimeMillis() / 1000).toULong()
            )

            randomMeasurements.add(randomMeasurement)
        }

        return randomMeasurements
    }

    // Checked (final)
    // Generates a list of the identifiers of the cells comprising the nearby environment of the client
    // at the discretization level used for nearby environments
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
            val nearbyAreaChildCells: List<String> = h3Core.cellToChildren(
                cellId,
                nearbyAreaDiscretizationLevel.toInt()
            )

            nearbyAreaChildCells.forEach { cellIdHexString ->
                nearbyAreaForClient.add(addLeadingZerosToCellId(cellIdHexString))
            }
        }

        return nearbyAreaForClient
    }

    // Checked (final)
    // Generates a list containing the identifier of the parent cell the client is located in along with its neighborhood
    // for the specified discretization level
    public fun getParentCellWithNeighborhood(
        clientCellId: String,
        levelOfParentCellAndNeighborhood: UShort
    ): List<String> {
        // Get identifier of the parent cell the client is located in for the specified cell level
        val parentCellId: String =
            addLeadingZerosToCellId(h3Core.cellToParentAddress(clientCellId, levelOfParentCellAndNeighborhood.toInt()))

        // Get the identifier of the neighboring cells surrounding the parent cell
        val neighborhoodCellIds: MutableList<String> = mutableListOf()

        for(neighborCellId in h3Core.gridRing(parentCellId, 1)){
            neighborhoodCellIds.add(addLeadingZerosToCellId(neighborCellId))
        }

        return listOf<String>(parentCellId).plus(neighborhoodCellIds)
    }

    // Checked (final)
    // Check the received FAMM if this contains measurements for distant areas where the discretization
    // level has to be adapted consequently
    public fun adaptionOfDiscretizationLevel(receivedFamm: ForeignAggregatedMeasurementMap): ForeignAggregatedMeasurementMap {
        // The adaption of the measurements discretization level depends on the location of the sensing client.
        // Therefore, a location value is mandatory!
        if (SimulatedMobileSensingClientsApplication.simulatedClientPosition != null) {
            val adaptedDiscretizationFamm: ForeignAggregatedMeasurementMap = receivedFamm
            val newMeasurements: MutableMap<String, Measurement> =
                mutableMapOf<String, Measurement>()
            val measurementsToBeRemoved: MutableMap<String, Measurement> =
                mutableMapOf<String, Measurement>()

            // Iterate over all measurements contained within the FAMM
            for (measurement in receivedFamm.getAllMeasurements()) {
                val measurementDiscretizationLevel: Int = h3Core.getResolution(measurement.key)

                // Check if the discretization level of the measurement is equal to the discretization level
                // for nearby areas. Only these can be adapted in discretization level!
                if (measurementDiscretizationLevel.toUShort() == SimulatedMobileSensingClientsApplication.nearbyAreaDiscretizationLevel) {
                    // Get the parent cell where the measurement is located in at the level for determining
                    // the nearby area
                    val parentCellForLevelToDetermineNearbyArea: String =
                        addLeadingZerosToCellId(h3Core.cellToParentAddress(
                            measurement.key,
                            SimulatedMobileSensingClientsApplication.levelToDetermineNearbyArea.toInt()
                        ))

                    // The cell is not located within the clients nearby area
                    if (!getParentCellWithNeighborhood(
                            SimulatedMobileSensingClientsApplication.simulatedClientPosition.cellIdHexString,
                            SimulatedMobileSensingClientsApplication.levelToDetermineNearbyArea
                        ).contains(parentCellForLevelToDetermineNearbyArea)
                    ) {
                        // Get the parent cell the measurement is located in for the discretization level used
                        // to discretize distant areas
                        val parentCellForLevelToDiscretizeDistantAreas: String = addLeadingZerosToCellId(
                            h3Core.cellToParentAddress(
                                measurement.key,
                                SimulatedMobileSensingClientsApplication.distantAreaDiscretizationLevel.toInt()
                            )
                        )

                        // This measurement is the first one processes from this FAMM which belongs to this
                        // parentCellForLevelToDiscretizeDistantAreas
                        if (!newMeasurements.containsKey(parentCellForLevelToDiscretizeDistantAreas)) {
                            // Set the original measurement included within the FAMM with the changed cell
                            // identifier in the map for the new measurements
                            val newMeasurement: Measurement = measurement.value
                            newMeasurement.cellIdHexString = parentCellForLevelToDiscretizeDistantAreas
                            newMeasurements[parentCellForLevelToDiscretizeDistantAreas] =
                                newMeasurement
                        }
                        // This measurement is not the first one processed from this FAMM which belongs to this
                        // parentCellForLevelToDiscretizeDistantAreas
                        else {
                            val newMeasurement: Measurement =
                                newMeasurements[parentCellForLevelToDiscretizeDistantAreas]!!

                            // Add the measurement value of the already existing new measurement and the currently
                            // discretized measurement
                            newMeasurement.measurementValue =
                                (newMeasurement.measurementValue + measurement.value.measurementValue).toUShort()

                            // Take the most current timestamp of both measurements
                            if (measurement.value.measurementCreationTimestamp > newMeasurement.measurementCreationTimestamp) {
                                newMeasurement.measurementCreationTimestamp =
                                    measurement.value.measurementCreationTimestamp
                            }

                            newMeasurements[parentCellForLevelToDiscretizeDistantAreas] =
                                newMeasurement
                        }

                        // Mark the measurement of which the discretization level was adapted for removal
                        measurementsToBeRemoved[measurement.key] = measurement.value
                    }
                }
            }

            logger.info("Number of measurements to be removed due to discretization of distant areas: ${measurementsToBeRemoved.size}")

            // Remove the measurements form which the discretization level has been adapted from the copy of the received FAMM
            adaptedDiscretizationFamm.removeMeasurementsForCellIds(measurementsToBeRemoved.keys.toList())

            logger.info("Number of newly created measurements to be added due to discretization of distant areas: ${measurementsToBeRemoved.size}")

            // Add all the new measurements resulting from the adaption of the discretization level the copy of the received FAMM
            newMeasurements.values.forEach { newMeasurement ->
                adaptedDiscretizationFamm.setMeasurement(newMeasurement)
            }

            // Return the FAMM with the discretized measurements
            return adaptedDiscretizationFamm
        }

        // Return the received FAMM if the sensing client has not yet a location value
        return receivedFamm
    }

    // Checked (final)
    // Check the LAMM if this contains two measurements for the same area each with another discretization level.
    // If this is the case, the measurement with the higher discretization level is chosen as this offers a
    // more detailed information about the crowd density for the corresponding area.
    public fun measurementPrioritization(lamm: LocalAggregatedMeasurementMap): LocalAggregatedMeasurementMap {
        val prioritizedMeasurementsLamm: LocalAggregatedMeasurementMap = lamm
        val measurementsToBeRemoved: MutableMap<String, Measurement> =
            mutableMapOf<String, Measurement>()
        val measurementsForDistantAreasWithinLamm: MutableMap<String, Measurement> =
            mutableMapOf<String, Measurement>()

        // Iterate over the entries within the LAMM, to find measurements for distant areas contained within the LAMM
        for (measurement in lamm.getAllMeasurements()) {
            if (h3Core.getResolution(measurement.value.cellIdHexString) == SimulatedMobileSensingClientsApplication.distantAreaDiscretizationLevel.toInt()) {
                measurementsForDistantAreasWithinLamm[measurement.key] = measurement.value
            }
        }

        // Iterate over the entries within the LAMM, to find measurements which correspond to the same area but have
        // different discretization levels
        for (measurement in lamm.getAllMeasurements()) {
            // Get discretization level of the current measurement
            val measurementDiscretizationLevel: Int = h3Core.getResolution(measurement.value.cellIdHexString)

            // Find measurements having the discretization level of nearby areas
            if (measurementDiscretizationLevel == SimulatedMobileSensingClientsApplication.nearbyAreaDiscretizationLevel.toInt()) {
                // Get the parent cell for this measurement for the discretization level used for distant areas
                val parentCellForDistantAreaDiscretizationLevel: String = addLeadingZerosToCellId(
                    h3Core.cellToParentAddress(
                        measurement.key,
                        SimulatedMobileSensingClientsApplication.distantAreaDiscretizationLevel.toInt()
                    )
                )

                // The LAMM contains measurements covering the same area with different discretization levels
                if (measurementsForDistantAreasWithinLamm.contains(
                        parentCellForDistantAreaDiscretizationLevel
                    )
                ) {
                    // Mark the measurement with the lower discretization level for removal
                    val measurementForDistantArea: Measurement =
                        measurementsForDistantAreasWithinLamm[parentCellForDistantAreaDiscretizationLevel]!!
                    measurementsToBeRemoved[measurementForDistantArea.cellIdHexString] =
                        measurementForDistantArea
                }
            }
        }

        logger.info("Number of measurements for distant areas to be removed because of more accurate data is present within the LAMM: ${measurementsToBeRemoved.size}")

        // Remove the measurements for which a measurement covering the same area with a larger
        // discretization level is present from the copy of the LAMM
        prioritizedMeasurementsLamm.removeMeasurementsForCellIds(measurementsToBeRemoved.keys.toList())

        // Return the LAMM with the prioritized measurements
        return prioritizedMeasurementsLamm
    }
}