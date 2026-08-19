package crowdDensityLibrary.dataClasses

// Source: https://kotlinlang.org/docs/serialization.html
import kotlinx.serialization.Serializable

/**
 * The measurement for a cell
 * @property cellIdHexString Cell identifier as hexadecimal string
 * @property measurementValue Measurement value
 * @property clientIdHexString Client identifier as hexadecimal string
 * @property measurementCreationTimestamp Timestamp of measurement creation in seconds
 */
@Serializable
data class Measurement(
    var cellIdHexString: String,
    var measurementValue: UShort,
    var clientIdHexString: String,
    var measurementCreationTimestamp: ULong
) {}