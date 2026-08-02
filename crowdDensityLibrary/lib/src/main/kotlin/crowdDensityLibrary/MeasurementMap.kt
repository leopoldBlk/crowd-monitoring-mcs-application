package crowdDensityLibrary

// Source: https://kotlinlang.org/docs/serialization.html
import kotlinx.serialization.Serializable
import crowdDensityLibrary.dataClasses.Measurement

@Serializable
abstract class MeasurementMap {
    // Mapping of cellIds to corresponding measurements
    protected var measurements: MutableMap<String, Measurement> = mutableMapOf<String, Measurement>()

    fun getMeasurement(cellId: String): Measurement? {
        return measurements[cellId]
    }

    fun setMeasurement(measurement: Measurement) {
        measurements[measurement.cellId] = measurement
    }

    fun addAllMeasurements(measurements: List<Measurement>) {
        for (measurement in measurements){
            setMeasurement(measurement)
        }
    }

    fun removeMeasurementForCellId(cellId: String) {
        measurements.remove(cellId)
    }

    fun removeMeasurementsForCellIds(cellIds: List<String>) {
        for (cellId in cellIds){
            removeMeasurementForCellId(cellId)
        }
    }

    fun getAllMeasurements(): MutableMap<String, Measurement> {
        return measurements
    }

    fun measurementForCellExists(cellId: String): Boolean {
        return measurements[cellId] != null
    }

    fun removeExceededMmEntries(timeToLiveInSeconds: ULong) {
        val measurementsToBeRemoved: MutableList<String> = mutableListOf<String>()

        measurements.forEach { (cellId, measurement) ->
            if (Utils.measurementExceededTtlInSeconds(measurement, timeToLiveInSeconds)) {
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
            updatedLmmMeasurement.clientId = clientId
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

//    fun removeMeasurementsWithMeasurementValueZero(){
//        val measurementsToBeRemoved: MutableList<String> = mutableListOf<String>()
//
//        measurements.forEach { (cellId, measurement) ->
//            if (measurement.measurementValue == 0u.toUShort()) {
//                measurementsToBeRemoved.add(cellId)
//            }
//        }
//
//        measurementsToBeRemoved.forEach { cellId ->
//            measurements.remove(cellId)
//        }
//    }
}