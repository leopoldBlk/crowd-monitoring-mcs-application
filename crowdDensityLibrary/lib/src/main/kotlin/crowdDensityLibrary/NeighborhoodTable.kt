package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.PositionalBeacon

class NeighborhoodTable {
    private var neighborhoodTableEntries: MutableMap<String, PositionalBeacon> =
        mutableMapOf<String, PositionalBeacon>()

    fun getNtEntry(clientId: String): PositionalBeacon? {
        return neighborhoodTableEntries[clientId]
    }

    fun setNtEntry(positionalBeacon: PositionalBeacon) {
        neighborhoodTableEntries[positionalBeacon.clientId] = positionalBeacon
    }

    fun getAllNtEntries(): MutableMap<String, PositionalBeacon> {
        return neighborhoodTableEntries
    }

    fun removeNtEntry(clientId: String) {
        neighborhoodTableEntries.remove(clientId)
    }

    fun getExceededNtEntries(timeToLiveInSeconds: ULong): MutableMap<String, PositionalBeacon> {
        val exceededPositionalBeacons: MutableMap<String, PositionalBeacon> =
            mutableMapOf<String, PositionalBeacon>()

        neighborhoodTableEntries.forEach { (clientId, positionalBeacon) ->
            if (Utils.positionalBeaconExceededTtlInSeconds(positionalBeacon, timeToLiveInSeconds)) {
                exceededPositionalBeacons[clientId] = positionalBeacon
            }
        }

        return exceededPositionalBeacons
    }

    fun removeExceededNtEntries(timeToLiveInSeconds: ULong) {
        val entriesToBeRemoved: MutableList<String> = mutableListOf<String>()

        neighborhoodTableEntries.forEach { (clientId, positionalBeacon) ->
            if (Utils.positionalBeaconExceededTtlInSeconds(positionalBeacon, timeToLiveInSeconds)) {
                entriesToBeRemoved.add(clientId)
            }
        }

        entriesToBeRemoved.forEach { clientId ->
            neighborhoodTableEntries.remove(clientId)
        }
    }
}