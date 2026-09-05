package server

import com.uber.h3core.H3Core
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
            if (h3Core.getResolution(measurement.value.cellIdHexString) == ServerApplication.distantAreaDiscretizationLevel.toInt()) {
                measurementsForDistantAreasWithinLamm[measurement.key] = measurement.value
            }
        }

        // Iterate over the entries within the LAMM, to find measurements which correspond to the same area but have
        // different discretization levels
        for (measurement in lamm.getAllMeasurements()) {
            // Get discretization level of the current measurement
            val measurementDiscretizationLevel: Int = h3Core.getResolution(measurement.value.cellIdHexString)

            // Find measurements having the discretization level of nearby areas
            if (measurementDiscretizationLevel == ServerApplication.nearbyAreaDiscretizationLevel.toInt()) {
                // Get the parent cell for this measurement for the discretization level used for distant areas
                val parentCellForDistantAreaDiscretizationLevel: String = addLeadingZerosToCellId(h3Core.cellToParentAddress(
                    measurement.key,
                    ServerApplication.distantAreaDiscretizationLevel.toInt()
                ))

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

        // Remove the measurements for which a measurement covering the same area with a larger
        // discretization level is present from the copy of the LAMM
        prioritizedMeasurementsLamm.removeMeasurementsForCellIds(measurementsToBeRemoved.keys.toList())

        // Return the LAMM with the prioritized measurements
        return prioritizedMeasurementsLamm
    }
}