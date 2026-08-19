package crowdDensityLibrary.dataClasses

import kotlinx.serialization.Serializable

// Checked (final)
@Serializable
data class Position(val coordinates: Coordinates, val cellIdHexString: String) {}