package crowdDensityLibrary.dataClasses

import kotlinx.serialization.Serializable

// Checked (final)
/**
 * Positional beacon for sensing process
 * @property clientIdHexString Identifier of the client sent the beacon as hexadecimal string
 * @property clientPosition Positional information of the client generated the beacon
 * @property clientTransmissionBenefit Transmission benefit of the client generated the beacon
 * @property beaconCreationTimestamp Timestamp of beacon creation in seconds
 */
@Serializable
data class PositionalBeacon(
    val clientIdHexString: String,
    val clientPosition: Position,
    val clientTransmissionBenefit: Float,
    val beaconCreationTimestamp: ULong
) {}