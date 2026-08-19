package crowdDensityLibrary

// Source: https://kotlinlang.org/docs/serialization.html
import crowdDensityLibrary.dataClasses.Measurement
import kotlinx.serialization.Serializable

// Checked (final)
@Serializable
abstract class MeasurementMap {
    // Mapping of cellIds to corresponding measurements
    protected var measurements: MutableMap<String, Measurement> = mutableMapOf<String, Measurement>()

    // Checked (final)
    fun getMeasurement(cellIdHexString: String): Measurement? {
        return measurements[cellIdHexString]
    }

    // Checked (final)
    fun getAllMeasurements(): MutableMap<String, Measurement> {
        return measurements
    }

    // Checked (final)
    fun measurementForCellExists(cellIdHexString: String): Boolean {
        return measurements[cellIdHexString] != null
    }

    // Checked (final)
    fun setMeasurement(measurement: Measurement) {
        measurements[measurement.cellIdHexString] = measurement
    }

    // Checked (final)
    fun removeMeasurementForCellId(cellIdHexString: String) {
        measurements.remove(cellIdHexString)
    }

    // Checked (final)
    fun removeMeasurementsForCellIds(cellIds: List<String>) {
        for (cellIdHexString in cellIds) {
            removeMeasurementForCellId(cellIdHexString)
        }
    }

    // Checked (final)
    fun removeExceededMmEntries(timeToLiveInMilliseconds: ULong) {
        val measurementsToBeRemoved: MutableList<String> = mutableListOf<String>()

        measurements.forEach { (cellId, measurement) ->
            if (Utils.checkMeasurementExceededTtl(measurement, timeToLiveInMilliseconds)) {
                measurementsToBeRemoved.add(cellId)
            }
        }

        measurementsToBeRemoved.forEach { cellId ->
            measurements.remove(cellId)
        }
    }

    // Checked (final)
    fun updateMeasurementCreationTime(cellIdHexString: String) {
        if (measurements.containsKey(cellIdHexString)) {
            val updatedLmmMeasurement: Measurement = measurements[cellIdHexString] as Measurement
            updatedLmmMeasurement.measurementCreationTimestamp = (System.currentTimeMillis() / 1000).toULong()
            measurements[cellIdHexString] = updatedLmmMeasurement
        }
    }
}