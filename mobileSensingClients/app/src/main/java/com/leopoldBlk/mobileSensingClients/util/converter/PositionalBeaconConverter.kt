package com.leopoldBlk.mobileSensingClients.util.converter

import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.PositionalBeaconTO
import crowdDensityLibrary.dataClasses.Coordinates
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon
import java.nio.ByteBuffer

// Checked (final)
object PositionalBeaconConverter {
    // Checked (final)
    public fun convertPositionalBeaconToPositionalBeaconTO(positionalBeacon: PositionalBeacon): PositionalBeaconTO {
        val clientId: ByteArray = positionalBeacon.clientIdHexString.hexToByteArray()

        val clientPositionLatitude: ByteArray = ByteBuffer.allocate(Double.SIZE_BYTES)
            .putDouble(positionalBeacon.clientPosition.coordinates.latitude).array()

        val clientPositionLongitude: ByteArray = ByteBuffer.allocate(Double.SIZE_BYTES)
            .putDouble(positionalBeacon.clientPosition.coordinates.longitude).array()

        // Convert cell identifier to optimized format for transmission
        val clientPositionCellId: ByteArray =
            CellConverter.compressUberH3CellIdHexString(positionalBeacon.clientPosition.cellIdHexString)
                .hexToByteArray()

        val transmissionBenefit: ByteArray = ByteBuffer.allocate(Float.SIZE_BYTES)
            .putFloat(positionalBeacon.clientTransmissionBenefit).array()

        // Capacity of 8 bytes necessary to convert long in byte array
        // --> Stripped to length of 4 bytes after conversion
        val timestampByteBuffer: ByteBuffer =
            ByteBuffer.allocate(8)
                .putLong(positionalBeacon.beaconCreationTimestamp.toLong())
        val timestampByteArray: ByteArray =
            timestampByteBuffer.array().drop(4).toByteArray()

        val newPositionalBeaconTO: PositionalBeaconTO = PositionalBeaconTO(
            clientId,
            clientPositionLatitude,
            clientPositionLongitude,
            clientPositionCellId,
            transmissionBenefit,
            timestampByteArray
        )

        return newPositionalBeaconTO
    }

    // Checked (final)
    public fun convertByteArrayToPositionalBeaconTO(byteArray: ByteArray): PositionalBeaconTO {
        // 4 byte for client identifier
        val clientId: ByteArray = byteArray.copyOfRange(0, 4)

        // 8 byte for latitude value of position
        val clientPositionLatitude: ByteArray = byteArray.copyOfRange(4, 12)

        // 8 byte for longitude value of position
        val clientPositionLongitude: ByteArray = byteArray.copyOfRange(12, 20)

        // 6 byte cell identifier (optimized Uber H3 Level 12 cellIds)
        val clientPositionCellId: ByteArray = byteArray.copyOfRange(20, 26)

        // 4 byte transmission benefit value
        val transmissionBenefit: ByteArray = byteArray.copyOfRange(26, 30)

        // 4 lower byte of unix timestamp (seconds)
        val beaconCreationTimestamp: ByteArray = byteArray.copyOfRange(30, 34)

        val positionalBeaconTO: PositionalBeaconTO = PositionalBeaconTO(
            clientId,
            clientPositionLatitude,
            clientPositionLongitude,
            clientPositionCellId,
            transmissionBenefit,
            beaconCreationTimestamp
        )

        return positionalBeaconTO
    }

    // Checked (final)
    public fun convertPositionalBeaconToToPositionalBeacon(positionalBeaconTO: PositionalBeaconTO): PositionalBeacon {
        val clientId: String = positionalBeaconTO.getClientId().toHexString()

        val clientPositionLatitude: Double =
            ByteBuffer.wrap(positionalBeaconTO.getLatitude()).getDouble()

        val clientPositionLongitude: Double =
            ByteBuffer.wrap(positionalBeaconTO.getLongitude()).getDouble()

        // Reconvert optimized format of cell identifier to Uber H3 compliant format
        val clientPositionCellId: String = CellConverter.decompressUberH3CellIdHexString(
            positionalBeaconTO.getCellId().toHexString()
        )

        val transmissionBenefit: Float =
            ByteBuffer.wrap(positionalBeaconTO.getTransmissionBenefit()).getFloat()

        // Insert leading zero bytes to fill up 8 bytes which are necessary for conversion of byte array to long
        val beaconCreationTimestamp: ULong =
            ByteBuffer.wrap(ByteArray(4) + positionalBeaconTO.getTimestamp()).getLong().toULong()

        val positionalBeacon: PositionalBeacon = PositionalBeacon(
            clientId, Position(
                Coordinates(clientPositionLatitude, clientPositionLongitude),
                clientPositionCellId
            ), transmissionBenefit, beaconCreationTimestamp
        )

        return positionalBeacon
    }
}