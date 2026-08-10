package com.leopoldBlk.mobileSensingClients

import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.DensityDataTO
import com.leopoldBlk.mobileSensingClients.util.converter.DensityDataConverter
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.nextUInt

class DensityDataConverterTest {
    private val localAggregatedMeasurementMapForTestWithoutSplitting: MeasurementMap =
        LocalAggregatedMeasurementMap()
    private val measurementOrderForTestWithoutSplitting: MutableList<String> =
        mutableListOf<String>()

    private val localAggregatedMeasurementMapForTestWithSplitting: MeasurementMap =
        LocalAggregatedMeasurementMap()
    private val measurementOrderForTestWithSplitting: MutableList<String> = mutableListOf<String>()

    @Before
    public fun prepareLammsAndOrdersForMeasurementTransmissionForTests() {
        val firstMeasurement: Measurement = Measurement(
            "08c1f8d7a4988bff", 10u, "01020304",
            // One hour before timestamp starting the test
            ((System.currentTimeMillis() / 1000) - 3600).toULong()
        )

        val secondMeasurement: Measurement = Measurement(
            "08c1f8d7a49883ff", 5u, "01020304",
            // Two hours before timestamp starting the test
            ((System.currentTimeMillis() / 1000) - 7200).toULong()
        )

        localAggregatedMeasurementMapForTestWithoutSplitting.setMeasurement(firstMeasurement)
        localAggregatedMeasurementMapForTestWithoutSplitting.setMeasurement(secondMeasurement)
        measurementOrderForTestWithoutSplitting.add("08c1f8d7a49883ff")
        measurementOrderForTestWithoutSplitting.add("08c1f8d7a4988bff")

        // Add 50 measurements to local aggregated measurement map with random cellId and measurement value
        for (measurementNumber in 1..49) {
            // Create random Uber H3 cell id with maximum level 13
            // --> Generate random byte array with 5 bytes
            // --> convert to binary representation and add padding up to length of 47 bits as this is the length which is needed
            // to encode every Uber H3 cell id in cell mode up to level 12
            var randomCellIdBinaryString: String =
                BigInteger(Random.nextBytes(ByteArray(5)).toHexString(), 16).toString(2)

            while (randomCellIdBinaryString.length < 47) {
                randomCellIdBinaryString = "0" + randomCellIdBinaryString
            }

            // Add the bits which are later optimized to correspond to Uber H3 cell id format
            randomCellIdBinaryString =
                "0" + "0001" + "000" + randomCellIdBinaryString + "111" + "111" + "111"

            // Convert the generated Uber H3 cell id to hexadecimal string
            var randomCellIdHexString = randomCellIdBinaryString.toBigInteger(2).toString(16)

            // Add leading zeros to binary string since these are removed with the conversion using toBigInteger(...).toString(...)
            while (randomCellIdHexString.length < 16) {
                randomCellIdHexString = "0" + randomCellIdHexString
            }

            val measurement: Measurement = Measurement(
                randomCellIdHexString,
                Random.nextUInt(UShort.MAX_VALUE.toUInt()).toUShort(),
                "01020304",
                // Minimum one hour before timestamp starting the test
                ((System.currentTimeMillis() / 1000) - 3600 - measurementNumber).toULong()
            )

            localAggregatedMeasurementMapForTestWithSplitting.setMeasurement(measurement)
            measurementOrderForTestWithSplitting.add(randomCellIdHexString)
        }

        // Randomly change the order for transmission of the measurements
        measurementOrderForTestWithSplitting.shuffle()
    }

    @Test
    public fun densityDataConverterTestWithoutSplitting() {
        val denistyDataTOs: List<DensityDataTO>? =
            DensityDataConverter.convertDensityDataToDensityDataTOs(
                Pair(
                    localAggregatedMeasurementMapForTestWithoutSplitting,
                    measurementOrderForTestWithoutSplitting
                )
            )

        val densityDataTOAsByteArray: ByteArray =
            denistyDataTOs!!.get(0).getDensityDataTOasByteArray()

        val reconvertedDensityDataTO: DensityDataTO =
            DensityDataConverter.convertByteArrayToDensityDataTO(densityDataTOAsByteArray)

        val reconvertedMeasurementMap: ForeignAggregatedMeasurementMap =
            DensityDataConverter.convertDensityDataTOsToDensityData(
                mutableListOf(reconvertedDensityDataTO)
            )

        assertEquals(
            localAggregatedMeasurementMapForTestWithoutSplitting.getAllMeasurements().size,
            reconvertedMeasurementMap.getAllMeasurements().size,
        )

        assertTrue(
            localAggregatedMeasurementMapForTestWithoutSplitting.getAllMeasurements().keys.containsAll(
                reconvertedMeasurementMap.getAllMeasurements().keys
            )
        )

        localAggregatedMeasurementMapForTestWithoutSplitting.getAllMeasurements()
            .forEach { (cellId, measurement) ->
                assertEquals(
                    localAggregatedMeasurementMapForTestWithoutSplitting.getMeasurement(cellId)!!.cellId,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.cellId
                )

                assertEquals(
                    localAggregatedMeasurementMapForTestWithoutSplitting.getMeasurement(cellId)!!.clientId,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.clientId
                )

                assertEquals(
                    localAggregatedMeasurementMapForTestWithoutSplitting.getMeasurement(cellId)!!.measurementCreationTimestamp,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.measurementCreationTimestamp
                )

                assertEquals(
                    localAggregatedMeasurementMapForTestWithoutSplitting.getMeasurement(cellId)!!.measurementValue,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.measurementValue
                )
            }
    }

    @Test
    public fun densityDataConverterTestWithSplitting() {
        val denistyDataTOs: List<DensityDataTO>? =
            DensityDataConverter.convertDensityDataToDensityDataTOs(
                Pair(
                    localAggregatedMeasurementMapForTestWithSplitting,
                    measurementOrderForTestWithSplitting
                )
            )

        val reconvertedDensityDataTOs: MutableList<DensityDataTO> = mutableListOf<DensityDataTO>()

        denistyDataTOs!!.forEach { densityDataTO ->
            val densityDataTOAsByteArray: ByteArray = densityDataTO.getDensityDataTOasByteArray()
            val reconvertedDensityDataTO: DensityDataTO =
                DensityDataConverter.convertByteArrayToDensityDataTO(densityDataTOAsByteArray)

            reconvertedDensityDataTOs.add(reconvertedDensityDataTO)
        }

        val reconvertedMeasurementMap: ForeignAggregatedMeasurementMap =
            DensityDataConverter.convertDensityDataTOsToDensityData(reconvertedDensityDataTOs)

        assertEquals(
            localAggregatedMeasurementMapForTestWithSplitting.getAllMeasurements().size,
            reconvertedMeasurementMap.getAllMeasurements().size,
        )

        assertTrue(
            localAggregatedMeasurementMapForTestWithSplitting.getAllMeasurements().keys.containsAll(
                reconvertedMeasurementMap.getAllMeasurements().keys
            )
        )

        localAggregatedMeasurementMapForTestWithSplitting.getAllMeasurements()
            .forEach { (cellId, measurement) ->
                assertEquals(
                    localAggregatedMeasurementMapForTestWithSplitting.getMeasurement(cellId)!!.cellId,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.cellId
                )

                assertEquals(
                    localAggregatedMeasurementMapForTestWithSplitting.getMeasurement(cellId)!!.clientId,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.clientId
                )

                assertEquals(
                    localAggregatedMeasurementMapForTestWithSplitting.getMeasurement(cellId)!!.measurementCreationTimestamp,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.measurementCreationTimestamp
                )

                assertEquals(
                    localAggregatedMeasurementMapForTestWithSplitting.getMeasurement(cellId)!!.measurementValue,
                    reconvertedMeasurementMap.getMeasurement(cellId)!!.measurementValue
                )
            }
    }
}