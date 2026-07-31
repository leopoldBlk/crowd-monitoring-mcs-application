package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MeasurementManager(
    private val clientId: String,
    private val timeToLiveInSeconds: ULong
) {
    private val neighborhoodTable: NeighborhoodTable = NeighborhoodTable()
    private val localMeasurementMap: LocalMeasurementMap = LocalMeasurementMap()
    private val foreignAggregatedMeasurementMaps: MutableMap<String, ForeignAggregatedMeasurementMap> =
        mutableMapOf<String, ForeignAggregatedMeasurementMap>()
    private var orderForMeasurementTransmission: MutableList<String> = mutableListOf<String>()

    // Mutex for ensuring exclusive access to and modification of density data to prevent unwanted effects due to concurrent modification
    private val operatingOnDensityDataMutex: Mutex = Mutex()

    private lateinit var localAggregatedMeasurementMap: LocalAggregatedMeasurementMap

    //Checked
    /**
     * Updates the NT and LMM according to received positional beacon
     * @param positionalBeacon Positional beacon for updating the NT and LMM
     */
    private fun updateNtAndLmm(positionalBeacon: PositionalBeacon) {
        val exceededPositionalBeacons: MutableMap<String, PositionalBeacon> =
            neighborhoodTable.getExceededNtEntries(timeToLiveInSeconds)

        exceededPositionalBeacons.forEach { (clientId, positionalBeacon) ->
            localMeasurementMap.decrementLmmMeasurementValue(positionalBeacon.clientPosition.cellId)
            neighborhoodTable.removeNtEntry(clientId)
        }

        // Positional beacon already reached TTL and will therefore not be processed
        if (Utils.positionalBeaconExceededTtlInSeconds(positionalBeacon, timeToLiveInSeconds)) {
            return
        }

        val currentNtEntry: PositionalBeacon? = neighborhoodTable.getNtEntry(positionalBeacon.clientId)
        val currentLmmMeasurement: Measurement? =
            localMeasurementMap.getMeasurement(positionalBeacon.clientPosition.cellId)

        // First positional beacon received from this client
        if (currentNtEntry == null) {
            // No measurement for cell identifier in positional beacon exits -> create new one
            if (currentLmmMeasurement == null) {
                val newLmmMeasurement: Measurement = Measurement(
                    positionalBeacon.clientPosition.cellId,
                    1u,
                    clientId,
                    (System.currentTimeMillis() / 1000).toULong()
                )
                localMeasurementMap.setMeasurement(newLmmMeasurement)
            }
            // Measurement for cell identifier in positional beacon already exits -> update existing one
            else {
                localMeasurementMap.incrementLmmMeasurementValue(positionalBeacon.clientPosition.cellId)
            }
        }

        // Not first positional beacon of client and client did not switch cells
        else if (currentNtEntry.clientPosition.cellId == positionalBeacon.clientPosition.cellId) {
            localMeasurementMap.updateMeasurementCreationTime(positionalBeacon.clientPosition.cellId)
        }

        // Not first positional beacon of client and client switched cells
        else {
            localMeasurementMap.decrementLmmMeasurementValue(currentNtEntry.clientPosition.cellId)

            // No measurement for cell identifier in positional beacon exits -> create new one
            if (currentLmmMeasurement == null) {
                val newLmmMeasurement: Measurement = Measurement(
                    positionalBeacon.clientPosition.cellId,
                    1u,
                    clientId,
                    (System.currentTimeMillis() / 1000).toULong()
                )
                localMeasurementMap.setMeasurement(newLmmMeasurement)
            }
            // Measurement for cell identifier in positional beacon already exits -> update existing one
            else {
                localMeasurementMap.incrementLmmMeasurementValue(positionalBeacon.clientPosition.cellId)
            }
        }

        neighborhoodTable.setNtEntry(positionalBeacon)
    }

    //Checked
    /**
     * Updates the current FAMM based on received FAMM for the corresponding client
     * @param senderClientId Identifier of the client sent the FAMM
     * @param reivedFamm FAMM the foreign client sent
     */
    private fun updateFamm(senderClientId: String, receivedFamm: ForeignAggregatedMeasurementMap) {
        var currentFamm: ForeignAggregatedMeasurementMap? = foreignAggregatedMeasurementMaps[senderClientId]

        if (currentFamm == null) {
            currentFamm = ForeignAggregatedMeasurementMap()
        }

        currentFamm.removeExceededMmEntries(timeToLiveInSeconds)
        receivedFamm.removeExceededMmEntries(timeToLiveInSeconds)

        receivedFamm.getAllMeasurements().forEach { (cellId, measurement) ->
            // Measurement for cell already exists in current FAMM for corresponding client
            if (currentFamm.measurementForCellExists(cellId)) {
                val currentMeasurement: Measurement = currentFamm.getMeasurement(cellId) as Measurement

                // Measurement for cell in received FAMM is newer than the one in current FAMM
                if (currentMeasurement.measurementCreationTimestamp < measurement.measurementCreationTimestamp) {
                    currentFamm.setMeasurement(measurement)
                }
            }
            // Measurement for cell does not already exist in current FAMM for corresponding client
            else {
                currentFamm.setMeasurement(measurement)
            }
        }

        foreignAggregatedMeasurementMaps[senderClientId] = currentFamm
    }

    //Checked
    /**
     * Generates the LAMM of the client based on its LMM and the FAMMs it has stored
     */
    private fun updateLamm() {
        val newLocalAggregatedMeasurementMap: LocalAggregatedMeasurementMap = LocalAggregatedMeasurementMap()

        // Contains all measurements per cell across the LMM and all FAMMs
        val totalMeasurementsPerCell: MutableMap<String, MutableSet<Measurement>> =
            mutableMapOf<String, MutableSet<Measurement>>()

        localMeasurementMap.getAllMeasurements().forEach { (cellId, measurement) ->
            totalMeasurementsPerCell[cellId] = mutableSetOf<Measurement>(measurement)
        }

        foreignAggregatedMeasurementMaps.forEach { (clientId, famm) ->
            famm.getAllMeasurements().forEach { (cellId, measurement) ->
                if (totalMeasurementsPerCell.containsKey(cellId)) {
                    totalMeasurementsPerCell[cellId]?.add(measurement)
                } else {
                    totalMeasurementsPerCell[cellId] = mutableSetOf<Measurement>(measurement)
                }
            }
        }

        totalMeasurementsPerCell.forEach { (cellId, measurements) ->
            val sortedMeasurements: List<Measurement> = measurements.sortedBy { it.measurementCreationTimestamp }

            // Youngest-Measurement-First as rank function for measurements
            newLocalAggregatedMeasurementMap.setMeasurement(sortedMeasurements.last())
            newLocalAggregatedMeasurementMap.updateMeasurementClientId(cellId, clientId)
        }

        localAggregatedMeasurementMap = newLocalAggregatedMeasurementMap
    }

    //Checked
    private fun updateOrderForMeasurementTransmission() {
        val orderedLammMeasurements: List<Pair<String, Measurement>> =
            localAggregatedMeasurementMap.getAllMeasurements().toList().sortedBy { (cellId, measurement) ->
                measurement.measurementCreationTimestamp
            }

        orderedLammMeasurements.forEach { (cellId, measurement) ->
            if (!orderForMeasurementTransmission.contains(cellId)) {
                orderForMeasurementTransmission.add(cellId)
            }
        }
    }

    public suspend fun handleReceivedPositionalBeacon(receivedPositionalBeacon: PositionalBeacon): Unit {
        // Lock mutex for exclusive access to and modification of the NT and LMM
        operatingOnDensityDataMutex.withLock {
            updateNtAndLmm(receivedPositionalBeacon)
        }
    }

    public suspend fun handleReceivedFamm(senderClientId: String, receivedFamm: ForeignAggregatedMeasurementMap): Unit {
        // Lock mutex for exclusive access to and modification of the corresponding FAMM
        operatingOnDensityDataMutex.withLock {
            updateFamm(senderClientId, receivedFamm)
        }
    }

    public suspend fun updateLammAndOrderForTransmission(): Pair<LocalAggregatedMeasurementMap, List<String>> {
        // Lock mutex for exclusive access to and modification of the LAMM and the order for transmission of the measurements
        operatingOnDensityDataMutex.withLock {
            updateLamm()
            updateOrderForMeasurementTransmission()
        }

        // Return the updated LAMM and order for transmission of the measurements
        return Pair<LocalAggregatedMeasurementMap, List<String>>(
            localAggregatedMeasurementMap,
            orderForMeasurementTransmission
        )
    }

    public fun getPositionalBeaconFromNtWithLargestTransmissionBenefit(): PositionalBeacon? {
        if (neighborhoodTable.getAllNtEntries().isNotEmpty()) {
            return neighborhoodTable.getAllNtEntries().maxBy { it.value.clientTransmissionBenefit }.value
        }
        return null
    }

    // For self updating the NT, LMM and FAMMs (removing entries exceeded the specified TTL)
    public suspend fun cleanUp(): Unit {
        // Lock mutex for exclusive access to and modification of the NT, the LMM and all the FAMMs
        operatingOnDensityDataMutex.withLock {
            neighborhoodTable.removeExceededNtEntries(timeToLiveInSeconds)

            localMeasurementMap.removeExceededMmEntries(timeToLiveInSeconds)

            foreignAggregatedMeasurementMaps.forEach { (clientId, map) ->
                map.removeExceededMmEntries(timeToLiveInSeconds)
            }
        }
    }

    public suspend fun overrideLmm(newMeasurements: List<Measurement>) {
        // Lock mutex for exclusive access to and modification of the LMM
        operatingOnDensityDataMutex.withLock {
            localMeasurementMap.override(newMeasurements)
        }
    }

    // For server, if clients request current lamm
    public fun getCurrentLammAndOrderForTransmission(): Pair<LocalAggregatedMeasurementMap, List<String>> {
        return Pair<LocalAggregatedMeasurementMap, List<String>>(
            localAggregatedMeasurementMap,
            orderForMeasurementTransmission
        )
    }
}