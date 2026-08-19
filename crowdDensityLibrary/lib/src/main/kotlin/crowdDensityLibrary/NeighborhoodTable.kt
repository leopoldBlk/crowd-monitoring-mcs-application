package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.PositionalBeacon

// Checked (final)
class NeighborhoodTable {
    // Stores positional beacons mapped by the identifier of the client (hexadecimal string) which sent the beacon
    private var neighborhoodTableEntries: MutableMap<String, PositionalBeacon> =
        mutableMapOf<String, PositionalBeacon>()

    // Checked (final)
    fun getNtEntry(clientIdHexString: String): PositionalBeacon? {
        return neighborhoodTableEntries[clientIdHexString]
    }

    // Checked (final)
    fun setNtEntry(positionalBeacon: PositionalBeacon) {
        neighborhoodTableEntries[positionalBeacon.clientIdHexString] = positionalBeacon
    }

    // Checked (final)
    fun getAllNtEntries(): MutableMap<String, PositionalBeacon> {
        return neighborhoodTableEntries
    }

    // Checked (final)
    fun removeNtEntry(clientIdHexString: String) {
        neighborhoodTableEntries.remove(clientIdHexString)
    }

    // Checked (final)
    fun getExceededNtEntries(timeToLiveInMilliseconds: ULong): MutableMap<String, PositionalBeacon> {
        val exceededPositionalBeacons: MutableMap<String, PositionalBeacon> =
            mutableMapOf<String, PositionalBeacon>()

        neighborhoodTableEntries.forEach { (clientIdHexString, positionalBeacon) ->
            if (Utils.checkPositionalBeaconExceededTtl(positionalBeacon, timeToLiveInMilliseconds)) {
                exceededPositionalBeacons[clientIdHexString] = positionalBeacon
            }
        }

        return exceededPositionalBeacons
    }
}