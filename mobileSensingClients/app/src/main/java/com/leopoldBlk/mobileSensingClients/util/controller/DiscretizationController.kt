package com.leopoldBlk.mobileSensingClients.util.controller

import android.util.Log
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.uber.h3core.H3Core
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.dataClasses.Measurement

object DiscretizationController {
    // No initialization at the beginning, because h3-lib is not accessible at the beginning
    private lateinit var h3Core: H3Core

    // Checked
    // Generates a list of the identifiers of the cells comprising the nearby environment of the client
    // at the discretization level used for nearby environments
    public fun getNearbyAreaForClient(
        clientCellId: String,
        nearbyAreaDiscretizationLevel: UShort,
        levelToDetermineNearbyArea: UShort
    ): List<String> {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        // Get the parent cell the client is located in along with its neighborhood for the level which is used
        // to determine the nearby area of a sensing client
        val nearbyAreaForClientParentCellWithNeighborhood: List<String> =
            getParentCellWithNeighborhood(clientCellId, levelToDetermineNearbyArea)

        val nearbyAreaForClient: MutableList<String> = mutableListOf()

        // Iterate over the parent cell and its neighborhood and add all their child cells at the level
        // used to discretize the nearby area of the sensing client
        for (cellId in nearbyAreaForClientParentCellWithNeighborhood) {
            nearbyAreaForClient.addAll(
                h3Core.cellToChildren(
                    cellId,
                    nearbyAreaDiscretizationLevel.toInt()
                )
            )
        }

        return nearbyAreaForClient
    }

    // Checked
    // Generates a list containing the identifier of the parent cell the client is located in along with its neighborhood
    // for the specified discretization level
    public fun getParentCellWithNeighborhood(
        clientCellId: String,
        levelOfParentCellAndNeighborhood: UShort
    ): List<String> {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        // Get identifier of the parent cell the client is located in for the specified cell level
        val parentCellId: String =
            h3Core.cellToParentAddress(clientCellId, levelOfParentCellAndNeighborhood.toInt())

        // Get the identifier of the neighboring cells surrounding the parent cell
        val neighborhoodCellIds: List<String> = h3Core.gridRing(parentCellId, 1)

        return listOf<String>(parentCellId).plus(neighborhoodCellIds)
    }

    // Checked logic & fits to algorithm of thesis
    // Check the received FAMM if this contains measurements for distant areas where the discretization
    // level has to be adapted consequently
    public fun adaptionOfDiscretizationLevel(receivedFamm: ForeignAggregatedMeasurementMap): ForeignAggregatedMeasurementMap {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        // The adaption of the measurements discretization level depends on the location of the sensing client.
        // Therefore, a location value is mandatory!
        if (DataStore.clientPosition.value != null) {
            val adaptedDiscretizationFamm: ForeignAggregatedMeasurementMap = receivedFamm
            val newMeasurements: MutableMap<String, Measurement> =
                mutableMapOf<String, Measurement>()
            val measurementsToBeRemoved: MutableMap<String, Measurement> =
                mutableMapOf<String, Measurement>()

            // Iterate over all measurements contained within the FAMM
            for (measurement in receivedFamm.getAllMeasurements()) {
                val measurementCellIdLevel: Int = h3Core.getResolution(measurement.key)

                // Check if the discretization level of the measurement is equal to the discretization level
                // for nearby areas. Only these can be adapted in discretization level!
                if (measurementCellIdLevel.toUShort() == DataStore.nearbyAreaDiscretizationLevel) {
                    // Get the parent cell where the measurement is located in at the level for determining
                    // the nearby area
                    val parentCellForLevelToDetermineNearbyArea: String =
                        h3Core.cellToParentAddress(
                            measurement.key,
                            DataStore.levelToDetermineNearbyArea.toInt()
                        )

                    // Discretization of the cell level has to be done if there parentCellForLevelToDetermineNearbyArea
                    // is not within the nearby area of the sensing client
                    if (!getParentCellWithNeighborhood(
                            DataStore.clientPosition.value!!.cellId,
                            DataStore.levelToDetermineNearbyArea
                        ).contains(parentCellForLevelToDetermineNearbyArea)
                    ) {
                        // Get the parent cell the measurement is located in for the discretization level used
                        // to discretize distant areas
                        val parentCellForLevelToDiscretizeDistantAreas: String =
                            h3Core.cellToParentAddress(
                                measurement.key,
                                DataStore.distantAreaDiscretizationLevel.toInt()
                            )

                        // This measurement is the first one processes from this FAMM which belongs to this
                        // parentCellForLevelToDiscretizeDistantAreas
                        if (!newMeasurements.containsKey(parentCellForLevelToDiscretizeDistantAreas)) {
                            // Set the original measurement included within the FAMM with the changed cell
                            // identifier in the map for the new measurements
                            val newMeasurement: Measurement = measurement.value
                            newMeasurement.cellId = parentCellForLevelToDiscretizeDistantAreas
                            newMeasurements[parentCellForLevelToDiscretizeDistantAreas] =
                                newMeasurement
                        }
                        // This measurement is not the first one processes from this FAMM which belongs to this
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

            LogController.log(
                Log.INFO,
                "DiscretizationController",
                "Number of measurements to be removed due to discretization of distant areas: ${measurementsToBeRemoved.size}"
            )

            // Remove the measurements form which the discretization level has been adapted from the copy of the received FAMM
            adaptedDiscretizationFamm.removeMeasurementsForCellIds(measurementsToBeRemoved.keys.toList())

            LogController.log(
                Log.INFO,
                "DiscretizationController",
                "Number of newly created measurements to be added due to discretization of distant areas: ${measurementsToBeRemoved.size}"
            )

            // Add all the new measurements resulting from the adaption of the discretization level the copy of the received FAMM
            adaptedDiscretizationFamm.addAllMeasurements(newMeasurements.values.toList())

            // Return the FAMM with the discretized measurements
            return adaptedDiscretizationFamm
        }

        // Return the received FAMM if the sensing client has not yet a location value
        return receivedFamm
    }

    // Checked logic & fits to algorithm of thesis (see TODO below)
    // Check the LAMM if this contains two measurements for the same area each with another discretization level.
    // If this is the case, the measurement with the higher discretization level is chosen as this offers a
    // more detailed information about the crowd density for the corresponding area.
    // TODO: Adapt the description of this algorithm in the thesis (no CDAM, but for-loop, measurementsForDistantAreas) --> More like pseudo code in thesis
    public fun measurementPrioritization(lamm: LocalAggregatedMeasurementMap): LocalAggregatedMeasurementMap {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        val prioritizedMeasurementsLamm: LocalAggregatedMeasurementMap = lamm
        val measurementsToBeRemoved: MutableMap<String, Measurement> =
            mutableMapOf<String, Measurement>()
        val measurementsForDistantAreasWithinLamm: MutableMap<String, Measurement> =
            mutableMapOf<String, Measurement>()

        // Iterate over the entries within the LAMM, to find measurements for distant areas with a
        // lower discretization level contained within the LAMM
        for (measurement in lamm.getAllMeasurements()) {
            if (h3Core.getResolution(measurement.value.cellId) == DataStore.distantAreaDiscretizationLevel.toInt()) {
                measurementsForDistantAreasWithinLamm[measurement.key] = measurement.value
            }
        }

        // Iterate over the entries within the LAMM, to find measurements which correspond to the same are but have
        // different discretization levels
        for (measurement in lamm.getAllMeasurements()) {
            // Get discretization level of the current measurement
            val measurementCellIdLevel: Int = h3Core.getResolution(measurement.value.cellId)

            // Find measurements having the discretization level of nearby areas
            if (measurementCellIdLevel == DataStore.nearbyAreaDiscretizationLevel.toInt()) {
                // Get the parent cell for this measurement for the discretization level used for distant areas
                val parentCellAtDistantAreaDiscretizationLevel: String = h3Core.cellToParentAddress(
                    measurement.key,
                    DataStore.distantAreaDiscretizationLevel.toInt()
                )

                // The LAMM contains measurements covering the same area with different discretization levels
                if (measurementsForDistantAreasWithinLamm.contains(
                        parentCellAtDistantAreaDiscretizationLevel
                    )
                ) {
                    // Mark the measurement with the lower discretization level for removal
                    val measurementForDistantArea: Measurement =
                        measurementsForDistantAreasWithinLamm[parentCellAtDistantAreaDiscretizationLevel]!!
                    measurementsToBeRemoved[measurementForDistantArea.cellId] =
                        measurementForDistantArea
                }
            }
        }

        LogController.log(
            Log.INFO,
            "DiscretizationController",
            "Number of measurements for distant areas to be removed because of more accurate data is present within the LAMM: ${measurementsToBeRemoved.size}"
        )

        // Remove the measurements for which a measurement covering the same area with a larger
        // discretization level is present from the copy of the LAMM
        prioritizedMeasurementsLamm.removeMeasurementsForCellIds(measurementsToBeRemoved.keys.toList())

        // Return the LAMM with the prioritized measurements
        return prioritizedMeasurementsLamm
    }
}