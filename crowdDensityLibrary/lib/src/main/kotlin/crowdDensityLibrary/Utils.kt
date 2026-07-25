package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.dataClasses.PositionalBeacon

class Utils {
    companion object {
        fun getAgeInMinutes(timestampToBeChecked: ULong): ULong {
            val currentTimestamp: ULong = (System.currentTimeMillis() / 1000).toULong()
            return (currentTimestamp - timestampToBeChecked) / 60u
        }

        fun getAgeInSeconds(timestampToBeChecked: ULong): ULong {
            val currentTimestamp: ULong = (System.currentTimeMillis() / 1000).toULong()
            return currentTimestamp - timestampToBeChecked
        }

        fun positionalBeaconExceededTtlInMinutes(
            positionalBeacon: PositionalBeacon,
            timeToLiveInMinutes: ULong
        ): Boolean {
            val positionalBeaconAgeInMinutes: ULong = getAgeInMinutes(positionalBeacon.beaconCreationTimestamp)
            return timeToLiveInMinutes < positionalBeaconAgeInMinutes
        }

        fun measurementExceededTtlInMinutes(measurement: Measurement, timeToLiveInMinutes: ULong): Boolean {
            val measurementAgeInMinutes: ULong = getAgeInMinutes(measurement.measurementCreationTimestamp)
            return timeToLiveInMinutes < measurementAgeInMinutes
        }

        fun positionalBeaconExceededTtlInSeconds(
            positionalBeacon: PositionalBeacon,
            timeToLiveInSeconds: ULong
        ): Boolean {
            val positionalBeaconAgeInSeconds: ULong = getAgeInSeconds(positionalBeacon.beaconCreationTimestamp)
            return timeToLiveInSeconds < positionalBeaconAgeInSeconds
        }

        fun measurementExceededTtlInSeconds(measurement: Measurement, timeToLiveInSeconds: ULong): Boolean {
            val measurementAgeInSeconds: ULong = getAgeInSeconds(measurement.measurementCreationTimestamp)
            return timeToLiveInSeconds < measurementAgeInSeconds
        }
    }
}