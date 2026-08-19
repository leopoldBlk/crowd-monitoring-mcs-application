package crowdDensityLibrary.dataClasses

import kotlinx.serialization.Serializable

/**
 * Coordinates defined by latitude and longitude
 * @property latitude Latitude value
 * @property longitude Longitude value
 */
@Serializable
data class Coordinates(val latitude: Double, val longitude: Double) {}