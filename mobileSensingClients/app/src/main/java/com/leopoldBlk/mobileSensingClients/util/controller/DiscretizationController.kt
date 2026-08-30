package com.leopoldBlk.mobileSensingClients.util.controller

import android.util.Log
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.converter.CellConverter.addLeadingZerosToCellId
import com.uber.h3core.H3Core
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import kotlinx.serialization.json.Json

// Checked (final)
object DiscretizationController {
    // No initialization at the beginning, because h3-lib is not accessible at the beginning
    private lateinit var h3Core: H3Core

    // Checked (final)
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

    // Checked (final)
    // Check the received FAMM if this contains measurements for distant areas where the discretization
    // level has to be adapted consequently
    public fun adaptionOfDiscretizationLevel(receivedFamm: ForeignAggregatedMeasurementMap): ForeignAggregatedMeasurementMap {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        LogController.logGeneralFunctionalityEvaluation(
            Log.INFO,
            "DiscretizationController",
            "[FR1-C5] FAMM to be discretized: ${
                Json { prettyPrint = true }.encodeToString(
                    receivedFamm
                )
            }"
        )

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
                val measurementDiscretizationLevel: Int = h3Core.getResolution(measurement.key)

                // Check if the discretization level of the measurement is equal to the discretization level
                // for nearby areas. Only these can be adapted in discretization level!
                if (measurementDiscretizationLevel.toUShort() == DataStore.nearbyAreaDiscretizationLevel) {
                    // Get the parent cell where the measurement is located in at the level for determining
                    // the nearby area
                    val parentCellForLevelToDetermineNearbyArea: String =
                        h3Core.cellToParentAddress(
                            measurement.key,
                            DataStore.levelToDetermineNearbyArea.toInt()
                        )

                    // The cell is not located within the clients nearby area
                    if (!getParentCellWithNeighborhood(
                            DataStore.clientPosition.value!!.cellIdHexString,
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
                            newMeasurement.cellIdHexString = addLeadingZerosToCellId(
                                parentCellForLevelToDiscretizeDistantAreas
                            )
                            newMeasurements[parentCellForLevelToDiscretizeDistantAreas] =
                                newMeasurement

                            LogController.logGeneralFunctionalityEvaluation(
                                Log.INFO,
                                "DiscretizationController",
                                "[FR1-C5] Create new measurement for parent cell ${newMeasurement.cellIdHexString} derived from child cell ${measurement.value.cellIdHexString} " +
                                        "with measurement value ${newMeasurement.measurementValue}"
                            )
                        }
                        // This measurement is not the first one processed from this FAMM which belongs to this
                        // parentCellForLevelToDiscretizeDistantAreas
                        else {
                            val newMeasurement: Measurement =
                                newMeasurements[parentCellForLevelToDiscretizeDistantAreas]!!

                            LogController.logGeneralFunctionalityEvaluation(
                                Log.INFO,
                                "DiscretizationController",
                                "[FR1-C5] Summing measurement values of parent cell ${newMeasurement.cellIdHexString} and child cell ${measurement.value.cellIdHexString}: " +
                                        "${newMeasurement.measurementValue} + ${measurement.value.measurementValue} = ${newMeasurement.measurementValue + measurement.value.measurementValue}"
                            )

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

            LogController.logGeneralFunctionalityEvaluation(
                Log.INFO,
                "DiscretizationController",
                "[FR1-C5] Number of measurements to be removed due to discretization of distant areas: ${measurementsToBeRemoved.size}"
            )

            // Remove the measurements form which the discretization level has been adapted from the copy of the received FAMM
            adaptedDiscretizationFamm.removeMeasurementsForCellIds(measurementsToBeRemoved.keys.toList())

            LogController.logGeneralFunctionalityEvaluation(
                Log.INFO,
                "DiscretizationController",
                "[FR1-C5] Number of newly created measurements to be added due to discretization of distant areas: ${newMeasurements.size}"
            )

            // Add all the new measurements resulting from the adaption of the discretization level the copy of the received FAMM
            newMeasurements.values.forEach { newMeasurement ->
                adaptedDiscretizationFamm.setMeasurement(newMeasurement)
            }

            LogController.logGeneralFunctionalityEvaluation(
                Log.INFO,
                "DiscretizationController",
                "[FR1-C5] FAMM after discretization: ${
                    Json { prettyPrint = true }.encodeToString(
                        adaptedDiscretizationFamm
                    )
                }"
            )

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
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        val prioritizedMeasurementsLamm: LocalAggregatedMeasurementMap = lamm
        val measurementsToBeRemoved: MutableMap<String, Measurement> =
            mutableMapOf<String, Measurement>()
        val measurementsForDistantAreasWithinLamm: MutableMap<String, Measurement> =
            mutableMapOf<String, Measurement>()

        // Iterate over the entries within the LAMM, to find measurements for distant areas contained within the LAMM
        for (measurement in lamm.getAllMeasurements()) {
            if (h3Core.getResolution(measurement.value.cellIdHexString) == DataStore.distantAreaDiscretizationLevel.toInt()) {
                measurementsForDistantAreasWithinLamm[measurement.key] = measurement.value
            }
        }

        // Iterate over the entries within the LAMM, to find measurements which correspond to the same area but have
        // different discretization levels
        for (measurement in lamm.getAllMeasurements()) {
            // Get discretization level of the current measurement
            val measurementDiscretizationLevel: Int =
                h3Core.getResolution(measurement.value.cellIdHexString)

            // Find measurements having the discretization level of nearby areas
            if (measurementDiscretizationLevel == DataStore.nearbyAreaDiscretizationLevel.toInt()) {
                // Get the parent cell for this measurement for the discretization level used for distant areas
                val parentCellForDistantAreaDiscretizationLevel: String = addLeadingZerosToCellId(
                    h3Core.cellToParentAddress(
                        measurement.key,
                        DataStore.distantAreaDiscretizationLevel.toInt()
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