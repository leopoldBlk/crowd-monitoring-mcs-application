package crowdDensityLibrary.dataClasses

// Source: https://kotlinlang.org/docs/serialization.html
import kotlinx.serialization.Serializable

/**
 * The measurement for a cell
 *
 * @property measurementValue The number of clients present in the observed cell
 * @property measurementMetadata The metadata of the measurement
 */
@Serializable
data class Measurement(
    var cellId: String,
    var measurementValue: UShort,
    var clientId: String,
    var measurementCreationTimestamp: ULong
) {}