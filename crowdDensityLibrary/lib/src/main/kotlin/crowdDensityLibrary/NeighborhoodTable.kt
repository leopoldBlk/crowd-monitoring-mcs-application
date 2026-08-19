package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.PositionalBeacon

class NeighborhoodTable {
    private var neighborhoodTableEntries: MutableMap<String, PositionalBeacon> =
        mutableMapOf<String, PositionalBeacon>()

    fun getNtEntry(clientId: String): PositionalBeacon? {
        return neighborhoodTableEntries[clientId]
    }

    fun setNtEntry(positionalBeacon: PositionalBeacon) {
        neighborhoodTableEntries[positionalBeacon.clientIdHexString] = positionalBeacon
    }

    fun getAllNtEntries(): MutableMap<String, PositionalBeacon> {
        return neighborhoodTableEntries
    }

    fun removeNtEntry(clientId: String) {
        neighborhoodTableEntries.remove(clientId)
    }

    fun getExceededNtEntries(timeToLiveInMilliseconds: ULong): MutableMap<String, PositionalBeacon> {
        val exceededPositionalBeacons: MutableMap<String, PositionalBeacon> =
            mutableMapOf<String, PositionalBeacon>()

        neighborhoodTableEntries.forEach { (clientId, positionalBeacon) ->
            if (Utils.checkPositionalBeaconExceededTtl(positionalBeacon, timeToLiveInMilliseconds)) {
                exceededPositionalBeacons[clientId] = positionalBeacon
            }
        }

        return exceededPositionalBeacons
    }
}