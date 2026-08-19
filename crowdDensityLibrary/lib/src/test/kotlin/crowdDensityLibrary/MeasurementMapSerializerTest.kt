package crowdDensityLibrary


import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.serializer.MeasurementMapSerializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementMapSerializerTest {
    val localAggregatedMeasurementMapTest: MeasurementMap =
        LocalAggregatedMeasurementMap()
    val orderForMeasurementTest: MutableList<String> =
        mutableListOf<String>()

    @BeforeTest
    public fun prepareTestData() {
        val firstMeasurement: Measurement = Measurement(
            "0d1f8d7a498c0bf",
            10u,
            "01020304",
            // One hour before timestamp starting the test
            ((System.currentTimeMillis() / 1000) - 3600).toULong()
        )

        val secondMeasurement: Measurement = Measurement(
            "0d1f8d7a498cccc",
            5u,
            "01020304",
            // Two hours before timestamp starting the test
            ((System.currentTimeMillis() / 1000) - 7200).toULong()
        )

        localAggregatedMeasurementMapTest.setMeasurement(firstMeasurement)
        localAggregatedMeasurementMapTest.setMeasurement(secondMeasurement)
        orderForMeasurementTest.add("0d1f8d7a498c0bf")
        orderForMeasurementTest.add("0d1f8d7a498cccc")
    }

    @Test
    public fun measurementMapSerializerTest() {
        val serializedMeasurementMapAsJson: String = MeasurementMapSerializer.serializeMeasurementMapToJson(
            localAggregatedMeasurementMapTest,
            orderForMeasurementTest
        )

        val deserializedMeasurementMapFromJson: MeasurementMap =
            MeasurementMapSerializer.deserializeJsonToMeasurementMap(serializedMeasurementMapAsJson)

        assertEquals(
            localAggregatedMeasurementMapTest.getAllMeasurements().size,
            deserializedMeasurementMapFromJson.getAllMeasurements().size
        )

        deserializedMeasurementMapFromJson.getAllMeasurements().forEach { (cellId, measurement) ->
            assertEquals(localAggregatedMeasurementMapTest.getMeasurement(cellId)!!.cellIdHexString, measurement.cellIdHexString)
            assertEquals(localAggregatedMeasurementMapTest.getMeasurement(cellId)!!.clientIdHexString, measurement.clientIdHexString)
            assertEquals(
                localAggregatedMeasurementMapTest.getMeasurement(cellId)!!.measurementValue,
                measurement.measurementValue
            )
            assertEquals(
                localAggregatedMeasurementMapTest.getMeasurement(cellId)!!.measurementCreationTimestamp,
                measurement.measurementCreationTimestamp
            )
        }
    }
}