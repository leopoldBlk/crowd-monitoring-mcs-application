package crowdDensityLibrary.serializer

import kotlinx.serialization.json.Json
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Measurement

object MeasurementMapSerializer {
    public fun serializeMeasurementMapToJson(
        measurementMap: MeasurementMap,
        orderForTransmission: List<String>
    ): String {
        val orderedListOfMeasurementsForTransmission: MutableList<Measurement> = mutableListOf<Measurement>()

        orderForTransmission.forEach { cellId ->
            val measurementFromMap: Measurement? = measurementMap.getMeasurement(cellId)

            if (measurementFromMap != null) {
                orderedListOfMeasurementsForTransmission.add(measurementFromMap)
            }
        }

        return Json.encodeToString(orderedListOfMeasurementsForTransmission)
    }

    public fun deserializeJsonToMeasurementMap(measurementMapAsJson: String): MeasurementMap {
        val foreignAggregatedMeasurementMap: MeasurementMap = ForeignAggregatedMeasurementMap()
        val listOfReceivedMeasurements: List<Measurement> = Json.decodeFromString(measurementMapAsJson)

        listOfReceivedMeasurements.forEach { measurement ->
            foreignAggregatedMeasurementMap.setMeasurement(measurement)
        }

        return foreignAggregatedMeasurementMap
    }
}