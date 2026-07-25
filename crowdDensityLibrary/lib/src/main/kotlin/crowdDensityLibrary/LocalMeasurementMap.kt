package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.Measurement

class LocalMeasurementMap : MeasurementMap() {
    /**
     * Generates the initial LMM
     * @param clientUuid Identifier of the client holding the LMM and generating the measurements
     * @param clientCoordinates Coordinates of the client holding the LMM and generating the measurements
     * @param neighborhoodTable NT the LMM should be initialized based on
     */
    fun generateInitialLmm(clientId: String, neighborhoodTable: NeighborhoodTable) {
        // Group entries in NT by cell identifier and create measurement for that cell
        neighborhoodTable.getAllNtEntries().entries.groupBy { it.value.clientPosition.cellId }
            .forEach { (cellId, positionalBeacons) ->
                val measurement: Measurement = Measurement(
                    cellId, positionalBeacons.size.toUShort(), clientId, (System.currentTimeMillis() / 1000).toULong()
                )

                measurements[cellId] = measurement
            }
    }

    /**
     * Increment a measurement value from the LMM
     * @param cellID Identifier of the cell from which the value has to be incremented
     * @param clientCoordinates Coordinates of the client to update metadata
     */
    fun incrementLmmMeasurementValue(cellId: String) {
        if (measurements.containsKey(cellId)) {
            val currentMeasurement: Measurement = measurements[cellId] as Measurement

            currentMeasurement.measurementValue = (currentMeasurement.measurementValue + 1u).toUShort()
            currentMeasurement.measurementCreationTimestamp = (System.currentTimeMillis() / 1000).toULong()

            measurements[cellId] = currentMeasurement
        }
    }

    //Checked
    /**
     * Decrement a measurement value from the LMM
     * @param cellID Identifier of the cell from which the value has to be decremented
     * @param clientCoordinates Coordinates of the client to update metadata
     */
    fun decrementLmmMeasurementValue(cellId: String) {
        if (measurements.containsKey(cellId)) {
            val currentMeasurement: Measurement = measurements[cellId] as Measurement

            currentMeasurement.measurementValue = (currentMeasurement.measurementValue - 1u).toUShort()
            currentMeasurement.measurementCreationTimestamp = (System.currentTimeMillis() / 1000).toULong()

            measurements[cellId] = currentMeasurement
        }
    }
}