package com.leopoldBlk.mobileSensingClients.services.wifiAware.densityData

import java.nio.ByteBuffer

class DensityDataTO(
    // 1 byte for identifier of the transmission
    private val transmissionId: Byte,
    // 1 byte for number of the DensityDataTO within the corresponding transmission
    private val numberOfDensityDataToWithinTransmission: Byte,
    // 4 byte for identifier of the sending client
    private val clientId: ByteArray,
    // 7 byte for unix timestamp in seconds (reference for timestamps in density data)
    private val timestamp: ByteArray,
) {
    // 242 byte for density data (cell measurements)
    private var densityData: MutableList<ByteArray> = mutableListOf<ByteArray>()

    public fun getTransmissionIdAsHexString(): String {
        return transmissionId.toHexString()
    }

    public fun getClientIdAsHexString(): String {
        return clientId.toHexString()
    }

    public fun getHeaderTimestampAsULong(): ULong {
        // Add byte to beginning to correspond to byte size of long for successful conversion
        return ByteBuffer.wrap(ByteArray(1) + timestamp).getLong().toULong()
    }

    public fun getDensityData(): List<ByteArray> {
        return densityData
    }

    public fun getDensityDataLength(): Int {
        return densityData.size
    }

    public fun addDensityData(densityData: ByteArray) {
        this.densityData.add(densityData)
    }

    public fun getDensityDataTOasByteArray(): ByteArray {
        // Converting bytes to byte arrays for addition
        val transmissionIdByteArray: ByteArray = ByteArray(1)
        transmissionIdByteArray[0] = transmissionId

        val numberOfDensityDataToWithinTransmissionByteArray: ByteArray = ByteArray(1)
        numberOfDensityDataToWithinTransmissionByteArray[0] =
            numberOfDensityDataToWithinTransmission

        var densityDataTOasByteArray: ByteArray =
            transmissionIdByteArray + numberOfDensityDataToWithinTransmissionByteArray + clientId + timestamp

        densityData.forEach { densityDataElement ->
            densityDataTOasByteArray += densityDataElement
        }

        return densityDataTOasByteArray
    }
}