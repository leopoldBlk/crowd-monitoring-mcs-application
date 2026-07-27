package com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon

class PositionalBeaconTO(
    // 4 byte for client identifier
    private val clientId: ByteArray,
    // 8 byte for latitude value of position
    private val latitude: ByteArray,
    // 8 byte for longitude value of position
    private val longitude: ByteArray,
    // 7 byte cell identifier (optimized Uber H3 Level 13 cellIds)
    private val cellId: ByteArray,
    // 4 byte transmission benefit value
    private val transmissionBenefit: ByteArray,
    // 7 byte for unix timestamp (seconds)
    private val timestamp: ByteArray
) {
    public fun getPositionalBeaconToAsByteArray(): ByteArray {
        val positionalBeaconToAsByteArray: ByteArray =
            clientId + latitude + longitude + cellId + transmissionBenefit + timestamp

        return positionalBeaconToAsByteArray
    }

    public fun getClientId(): ByteArray {
        return clientId
    }

    public fun getLatitude(): ByteArray {
        return latitude
    }

    public fun getLongitude(): ByteArray {
        return longitude
    }

    public fun getCellId(): ByteArray {
        return cellId
    }

    public fun getTransmissionBenefit(): ByteArray {
        return transmissionBenefit
    }

    public fun getTimestamp(): ByteArray {
        return timestamp
    }
}