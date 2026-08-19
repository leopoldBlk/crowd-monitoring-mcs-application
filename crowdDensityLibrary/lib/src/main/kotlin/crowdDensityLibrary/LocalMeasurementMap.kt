package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.Measurement

class LocalMeasurementMap : MeasurementMap() {
    // Checked (final)
    /**
     * Increment a measurement value from the LMM
     * @param cellIdHexString Identifier of the cell from which the value has to be incremented
     */
    fun incrementLmmMeasurementValue(cellIdHexString: String) {
        if (measurements.containsKey(cellIdHexString)) {
            val currentMeasurement: Measurement = measurements[cellIdHexString] as Measurement

            currentMeasurement.measurementValue = (currentMeasurement.measurementValue + 1u).toUShort()
            currentMeasurement.measurementCreationTimestamp = (System.currentTimeMillis() / 1000).toULong()

            measurements[cellIdHexString] = currentMeasurement
        }
    }

    // Checked (final)
    /**
     * Decrement a measurement value from the LMM
     * @param cellIdHexString Identifier of the cell from which the value has to be decremented
     */
    fun decrementLmmMeasurementValue(cellIdHexString: String) {
        if (measurements.containsKey(cellIdHexString)) {
            val currentMeasurement: Measurement = measurements[cellIdHexString] as Measurement

            currentMeasurement.measurementValue = (currentMeasurement.measurementValue - 1u).toUShort()
            currentMeasurement.measurementCreationTimestamp = (System.currentTimeMillis() / 1000).toULong()

            measurements[cellIdHexString] = currentMeasurement
        }
    }

    // Checked (final)
    fun setAllMeasurements(newMeasurements: List<Measurement>) {
        measurements.clear()

        for (newMeasurement in newMeasurements) {
            measurements[newMeasurement.cellIdHexString] = newMeasurement
        }
    }
}