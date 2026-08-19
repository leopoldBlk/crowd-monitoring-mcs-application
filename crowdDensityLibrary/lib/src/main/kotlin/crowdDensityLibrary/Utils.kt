package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.dataClasses.PositionalBeacon

// Checked (final)
class Utils {
    companion object {
        // Checked (final)
        fun getAgeInMilliseconds(timestampToBeChecked: ULong): ULong {
            return System.currentTimeMillis().toULong() - timestampToBeChecked
        }

        // Checked (final)
        fun checkPositionalBeaconExceededTtl(
            positionalBeacon: PositionalBeacon,
            timeToLiveInMilliseconds: ULong
        ): Boolean {
            // Multiply with factor 1000 as the beacon creation timestamp is stored with second accuracy
            val positionalBeaconAgeInMilliseconds: ULong =
                getAgeInMilliseconds(positionalBeacon.beaconCreationTimestamp * 1000u)
            return timeToLiveInMilliseconds < positionalBeaconAgeInMilliseconds
        }

        // Checked (final)
        fun checkMeasurementExceededTtl(measurement: Measurement, timeToLiveInMilliseconds: ULong): Boolean {
            // Multiply with factor 1000 as the measurement creation timestamp is stored with second accuracy
            val measurementAgeInMilliseconds: ULong =
                getAgeInMilliseconds(measurement.measurementCreationTimestamp * 1000u)
            return timeToLiveInMilliseconds < measurementAgeInMilliseconds
        }
    }
}