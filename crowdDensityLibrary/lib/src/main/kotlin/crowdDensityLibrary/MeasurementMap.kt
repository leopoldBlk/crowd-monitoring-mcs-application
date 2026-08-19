package crowdDensityLibrary

// Source: https://kotlinlang.org/docs/serialization.html
import crowdDensityLibrary.dataClasses.Measurement
import kotlinx.serialization.Serializable

@Serializable
abstract class MeasurementMap {
    // Mapping of cellIds to corresponding measurements
    protected var measurements: MutableMap<String, Measurement> = mutableMapOf<String, Measurement>()

    fun getMeasurement(cellId: String): Measurement? {
        return measurements[cellId]
    }

    fun getAllMeasurements(): MutableMap<String, Measurement> {
        return measurements
    }

    fun measurementForCellExists(cellId: String): Boolean {
        return measurements[cellId] != null
    }

    fun setMeasurement(measurement: Measurement) {
        measurements[measurement.cellIdHexString] = measurement
    }

    fun removeMeasurementForCellId(cellId: String) {
        measurements.remove(cellId)
    }

    fun removeMeasurementsForCellIds(cellIds: List<String>) {
        for (cellId in cellIds) {
            removeMeasurementForCellId(cellId)
        }
    }

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

    fun updateMeasurementClientId(cellId: String, clientId: String) {
        if (measurements.containsKey(cellId)) {
            val updatedLmmMeasurement: Measurement = measurements[cellId] as Measurement
            updatedLmmMeasurement.clientIdHexString = clientId
            measurements[cellId] = updatedLmmMeasurement
        }
    }

    fun updateMeasurementCreationTime(cellId: String) {
        if (measurements.containsKey(cellId)) {
            val updatedLmmMeasurement: Measurement = measurements[cellId] as Measurement
            // Get UNIX epoch time with accuracy of seconds
            updatedLmmMeasurement.measurementCreationTimestamp = (System.currentTimeMillis() / 1000).toULong()
            measurements[cellId] = updatedLmmMeasurement
        }
    }

}