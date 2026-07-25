package crowdDensityLibrary.dataClasses

/**
 * Positional beacon for sensing process
 * @property clientId Identifier of the client generated the beacon
 * @property position Coordinates of the client generated the beacon
 * @property creationTime The creation time of the beacon
 * @property cellID Identifier of the cell the client generated the beacon is located within
 */
data class PositionalBeacon(
    val clientId: String,
    val clientPosition: Position,
    val clientTransmissionBenefit: Float,
    val beaconCreationTimestamp: ULong
) {}