package com.leopoldBlk.mobileSensingClients.util.converter

import android.util.Log
import com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData.DensityDataTO
import com.leopoldBlk.mobileSensingClients.util.controller.LogController
import crowdDensityLibrary.ForeignAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import java.nio.ByteBuffer
import kotlin.random.Random

// Checked (final)
object DensityDataConverter {
    // Length of a total data packet for D2D data transmission in bytes
    // --> for Wifi Aware Service Discovery: 255 byte
    private val totalDataPacketLengthInBytes: UInt = 255u

    // Header length of data transmission with on data packet
    // --> 1 byte transmissionId + 1 byte packetId + 4 byte clientId + 4 byte unix timestamp (seconds)
    private val headerLengthInBytes: UInt = 10u

    // Bytes in data packet left for application data (density data in this case)
    private val bytesInDataPacketForDensityData: UInt =
        totalDataPacketLengthInBytes - headerLengthInBytes

    // Bytes needed for encoding one cell measurement (density data element)
    // --> 6 byte cellId (compressed at maximum Uber H3 level 12) + 2 byte measurement value + 2 byte timestamp (reference to timestamp in header)
    private val bytesPerMeasurement: UInt = 6u + 2u + 2u

    // Checked (final)
    public fun convertDensityDataToDensityDataTOs(lammAndOrderForMeasurementTransmission: Pair<MeasurementMap, List<String>>): List<DensityDataTO>? {
        val localAggregatedMeasurementMap: MeasurementMap =
            lammAndOrderForMeasurementTransmission.first
        val orderForMeasurementTransmission: List<String> =
            lammAndOrderForMeasurementTransmission.second

        // Only convert if the local aggregated measurement map holds measurements
        if (localAggregatedMeasurementMap.getAllMeasurements().isNotEmpty()) {
            val transmissionId: Byte = Random.nextBytes(1).first()
            var numberOfDensityDataToWithinTransmission: Byte = 1
            val densityDataTOs: MutableList<DensityDataTO> = mutableListOf<DensityDataTO>()
            var referenceTimestampOfLastDensityDataTO: ULong? = null

            orderForMeasurementTransmission.forEach { cellId ->
                val measurement: Measurement? = localAggregatedMeasurementMap.getMeasurement(cellId)

                if (measurement != null) {
                    // There is already a DensityDataTO created for this transmission
                    if (densityDataTOs.isNotEmpty()) {
                        // Get the amount of measurements within the current DensityDataTO
                        val densityDataInCurrentDensityDataTO: UInt =
                            densityDataTOs.last().getDensityDataLength().toUInt()

                        // The already existing DensityDataTO is not able to fit an additional measurement due to size limitation
                        // --> a new DensityDataTO has to be generated
                        if ((densityDataInCurrentDensityDataTO + 1u) > (bytesInDataPacketForDensityData.floorDiv(
                                bytesPerMeasurement
                            ))
                        ) {
                            referenceTimestampOfLastDensityDataTO =
                                (System.currentTimeMillis() / 1000).toULong()

                            // Create byte buffer with 8 bytes for storing timestamp as long
                            // --> Remove first 4 bytes since only the lower 4 byte are used as timestamp (seconds)
                            val timestampByteBuffer: ByteBuffer =
                                ByteBuffer.allocate(8)
                                    .putLong(referenceTimestampOfLastDensityDataTO.toLong())
                            val timestampByteArray: ByteArray =
                                timestampByteBuffer.array().drop(4).toByteArray()

                            val newDensityDataTO: DensityDataTO = DensityDataTO(
                                transmissionId,
                                numberOfDensityDataToWithinTransmission,
                                measurement.clientIdHexString.hexToByteArray(),
                                timestampByteArray
                            )
                            densityDataTOs.add(newDensityDataTO)
                            numberOfDensityDataToWithinTransmission =
                                numberOfDensityDataToWithinTransmission.inc()
                        }
                    }
                    // A DensityDataTO is not yet created for this transmission
                    else {
                        referenceTimestampOfLastDensityDataTO =
                            (System.currentTimeMillis() / 1000).toULong()

                        // Create byte buffer with 8 bytes for storing timestamp as long
                        // --> Remove first 4 bytes since only the lower 4 byte are used as timestamp (seconds)
                        val timestampByteBuffer: ByteBuffer =
                            ByteBuffer.allocate(8)
                                .putLong(referenceTimestampOfLastDensityDataTO.toLong())
                        val timestampByteArray: ByteArray =
                            timestampByteBuffer.array().drop(4).toByteArray()

                        val newDensityDataTO: DensityDataTO = DensityDataTO(
                            transmissionId,
                            numberOfDensityDataToWithinTransmission,
                            measurement.clientIdHexString.hexToByteArray(),
                            timestampByteArray
                        )
                        densityDataTOs.add(newDensityDataTO)
                        numberOfDensityDataToWithinTransmission =
                            numberOfDensityDataToWithinTransmission.inc()
                    }

                    // Convert measurement to byte array
                    val measurementAsByteArray: ByteArray =
                        convertMeasurementToByteArray(
                            measurement,
                            referenceTimestampOfLastDensityDataTO!!
                        )

                    // Add measurement as byte array to current DensityDataTO of transmission
                    densityDataTOs.last().addDensityData(measurementAsByteArray)
                }
            }
            return densityDataTOs
        }
        return null
    }

    // Checked (final)
    private fun convertMeasurementToByteArray(
        measurement: Measurement,
        referenceTimestampOfLastDensityDataTO: ULong
    ): ByteArray {
        val measurementCellIdByteArray: ByteArray =
            CellConverter.compressUberH3CellIdHexString(measurement.cellIdHexString)
                .hexToByteArray()

        val measurementValueByteArray: ByteArray =
            ByteBuffer.allocate(UShort.SIZE_BYTES).putShort(measurement.measurementValue.toShort())
                .array()

        val measurementCreationTimestampReferenced: ULong =
            referenceTimestampOfLastDensityDataTO - measurement.measurementCreationTimestamp
        val measurementCreationTimestampByteBuffer: ByteBuffer =
            ByteBuffer.allocate(8).putLong(measurementCreationTimestampReferenced.toLong())
        // Timestamp for measurement is stripped to size of 2 bytes, which fits 65,536s (ca. 18.20h)
        val measurementCreationTimestampByteArray: ByteArray =
            measurementCreationTimestampByteBuffer.array().drop(6).toByteArray()

        val byteArrayForTransmission: ByteArray =
            measurementCellIdByteArray + measurementValueByteArray + measurementCreationTimestampByteArray

        LogController.logGeneralFunctionalityEvaluation(
            Log.INFO,
            "DensityDataConverter",
            "[FR1-C7] Measurement has size ${byteArrayForTransmission.size} and is composed of: " +
                    "$measurementCellIdByteArray (cellId - ${measurementCellIdByteArray.size} bytes) + " +
                    "$measurementValueByteArray (measurementValue - ${measurementValueByteArray.size} bytes) + " +
                    "$measurementCreationTimestampByteArray (timestamp - ${measurementCreationTimestampByteArray.size} bytes)"
        )

        return byteArrayForTransmission
    }

    // Checked (final)
    public fun convertByteArrayToDensityDataTO(byteArray: ByteArray): DensityDataTO {
        // Extract the different parts composing a DensityDataTO from the byte array
        val transmissionId: ByteArray = byteArray.copyOfRange(0, 1)
        val numberOfDensityDataToWithinTransmission: ByteArray = byteArray.copyOfRange(1, 2)
        val clientId: ByteArray = byteArray.copyOfRange(2, 6)
        val referenceTimestamp: ByteArray = byteArray.copyOfRange(6, 10)
        val densityData: ByteArray = byteArray.copyOfRange(10, byteArray.size)

        val densityDataTO: DensityDataTO = DensityDataTO(
            transmissionId[0],
            numberOfDensityDataToWithinTransmission[0],
            clientId,
            referenceTimestamp
        )

        val amountOfDensityDataElements: UInt =
            densityData.size.toUInt().floorDiv(bytesPerMeasurement)

        // Iterate over all density data elements (cell measurements) and insert them into DensityDataTO
        for (densityDataElementNumber in 0u until amountOfDensityDataElements) {
            val densityDataElement: ByteArray = densityData.copyOfRange(
                (densityDataElementNumber * bytesPerMeasurement).toInt(),
                ((densityDataElementNumber * bytesPerMeasurement) + bytesPerMeasurement).toInt()
            )

            densityDataTO.addDensityData(densityDataElement)
        }

        return densityDataTO
    }

    // Checked (final)
    public fun convertDensityDataTOsToDensityData(densityDataTOs: MutableList<DensityDataTO>): ForeignAggregatedMeasurementMap {
        val foreignAggregatedMeasurementMap: ForeignAggregatedMeasurementMap =
            ForeignAggregatedMeasurementMap()

        // Iterate over all DensityDataTOs
        densityDataTOs.forEach { densityDataTO ->
            val senderClientId: String = densityDataTO.getClientIdAsHexString()
            val referenceHeaderTimestamp: ULong = densityDataTO.getHeaderTimestampAsULong()

            // Iterate over all density data elements, covert them to a measurement and insert the measurement to the measurement map
            densityDataTO.getDensityData().forEach { densityDataElement ->
                val measurement: Measurement = convertByteArrayToMeasurement(
                    senderClientId,
                    densityDataElement,
                    referenceHeaderTimestamp
                )
                foreignAggregatedMeasurementMap.setMeasurement(measurement)
            }
        }

        return foreignAggregatedMeasurementMap
    }

    // Checked (final)
    private fun convertByteArrayToMeasurement(
        senderClientId: String,
        measurementByteArray: ByteArray,
        referenceHeaderTimestamp: ULong
    ): Measurement {
        // 6 byte cellId (optimized Uber H3 Level 12 cellIds)
        val measurementCellId: String = CellConverter.decompressUberH3CellIdHexString(
            measurementByteArray.copyOfRange(0, 6).toHexString()
        )

        // 2 byte measurement value
        val measurementValue: UShort =
            ByteBuffer.wrap(measurementByteArray.copyOfRange(6, 8)).getShort().toUShort()

        // 4 byte clientId already as hexadecimal string
        val measurementClientId: String = senderClientId

        // Add 6 bytes to beginning since timestamp for measurement is encoded with 2 bytes in reference to header timestamp
        val densityDataElementTimestamp: ULong =
            ByteBuffer.wrap(ByteArray(6) + measurementByteArray.copyOfRange(8, 10)).getLong()
                .toULong()

        val measurement: Measurement = Measurement(
            measurementCellId,
            measurementValue,
            measurementClientId,
            referenceHeaderTimestamp - densityDataElementTimestamp
        )

        return measurement
    }
}