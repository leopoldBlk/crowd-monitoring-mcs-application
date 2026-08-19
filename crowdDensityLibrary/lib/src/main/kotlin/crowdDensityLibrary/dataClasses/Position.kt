package crowdDensityLibrary.dataClasses

import kotlinx.serialization.Serializable

@Serializable
data class Position(val coordinates: Coordinates, val cellIdHexString: String) {}