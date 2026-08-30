package crowdDensityLibrary

import crowdDensityLibrary.dataClasses.Measurement
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Checked (final)
class MeasurementManager(
    private val clientIdHexString: String,
    private val timeToLiveNeighborhoodTableInMilliseconds: ULong,
    private val timeToLiveMeasurementMapInMilliseconds: ULong
) {
    private val neighborhoodTable: NeighborhoodTable = NeighborhoodTable()
    private val localMeasurementMap: LocalMeasurementMap = LocalMeasurementMap()
    private val foreignAggregatedMeasurementMaps: MutableMap<String, ForeignAggregatedMeasurementMap> =
        mutableMapOf<String, ForeignAggregatedMeasurementMap>()

    // List of cell identifiers which indicates the order for transmitting the measurements
    private var orderForMeasurementTransmission: MutableList<String> = mutableListOf<String>()

    // Mutex for ensuring exclusive access to and modification of density data to prevent unwanted effects due to concurrent modification
    private val operatingOnDensityDataMutex: Mutex = Mutex()

    private lateinit var localAggregatedMeasurementMap: LocalAggregatedMeasurementMap

    //Checked (final)
    /**
     * Updates the NT and LMM according to received positional beacon
     * @param positionalBeacon Positional beacon for updating the NT and LMM
     */
    private fun updateNtAndLmm(positionalBeacon: PositionalBeacon) {
        val neighborhoodTableIterator = neighborhoodTable.getAllNtEntries().iterator()

        while (neighborhoodTableIterator.hasNext()) {
            val neighborhoodTableEntry = neighborhoodTableIterator.next()

            if (Utils.checkPositionalBeaconExceededTtl(
                    neighborhoodTableEntry.value,
                    timeToLiveNeighborhoodTableInMilliseconds
                )
            ) {
                localMeasurementMap.decrementLmmMeasurementValue(neighborhoodTableEntry.value.clientPosition.cellIdHexString)
                neighborhoodTableIterator.remove()
            }
        }

        // Positional beacon already reached TTL and will therefore not be processed
        if (Utils.checkPositionalBeaconExceededTtl(positionalBeacon, timeToLiveNeighborhoodTableInMilliseconds)) {
            return
        }

        val currentNtEntry: PositionalBeacon? = neighborhoodTable.getNtEntry(positionalBeacon.clientIdHexString)
        val currentLmmMeasurement: Measurement? =
            localMeasurementMap.getMeasurement(positionalBeacon.clientPosition.cellIdHexString)

        // First positional beacon received from this client
        if (currentNtEntry == null) {
            // No measurement for cell identifier in positional beacon exits -> create new one
            if (currentLmmMeasurement == null) {
                val newLmmMeasurement: Measurement = Measurement(
                    positionalBeacon.clientPosition.cellIdHexString,
                    1u,
                    clientIdHexString,
                    (System.currentTimeMillis() / 1000).toULong()
                )
                localMeasurementMap.setMeasurement(newLmmMeasurement)
            }
            // Measurement for cell identifier in positional beacon already exits -> update existing one
            else {
                localMeasurementMap.incrementLmmMeasurementValue(positionalBeacon.clientPosition.cellIdHexString)
            }
        }

        // Not first positional beacon of client and client did not switch cells -> only update timestamp
        else if (currentNtEntry.clientPosition.cellIdHexString == positionalBeacon.clientPosition.cellIdHexString) {
            localMeasurementMap.updateMeasurementCreationTime(positionalBeacon.clientPosition.cellIdHexString)
        }

        // Not first positional beacon of client and client switched cells
        else {
            localMeasurementMap.decrementLmmMeasurementValue(currentNtEntry.clientPosition.cellIdHexString)

            // No measurement for cell identifier in positional beacon exits -> create new one
            if (currentLmmMeasurement == null) {
                val newLmmMeasurement: Measurement = Measurement(
                    positionalBeacon.clientPosition.cellIdHexString,
                    1u,
                    clientIdHexString,
                    (System.currentTimeMillis() / 1000).toULong()
                )
                localMeasurementMap.setMeasurement(newLmmMeasurement)
            }
            // Measurement for cell identifier in positional beacon already exits -> update existing one
            else {
                localMeasurementMap.incrementLmmMeasurementValue(positionalBeacon.clientPosition.cellIdHexString)
            }
        }

        neighborhoodTable.setNtEntry(positionalBeacon)
    }

    // Checked (final)
    /**
     * Updates the current FAMM based on received FAMM for the corresponding client
     * @param senderClientIdHexString Identifier of the client sent the FAMM
     * @param receivedFamm FAMM the foreign client sent
     */
    private fun updateFamm(senderClientIdHexString: String, receivedFamm: ForeignAggregatedMeasurementMap) {
        var currentFamm: ForeignAggregatedMeasurementMap? = foreignAggregatedMeasurementMaps[senderClientIdHexString]

        if (currentFamm == null) {
            currentFamm = ForeignAggregatedMeasurementMap()
        }

        currentFamm.removeExceededMmEntries(timeToLiveMeasurementMapInMilliseconds)
        receivedFamm.removeExceededMmEntries(timeToLiveMeasurementMapInMilliseconds)

        receivedFamm.getAllMeasurements().forEach { (cellIdHexString, receivedMeasurement) ->
            // Measurement for cell already exists in current FAMM for corresponding client
            if (currentFamm.measurementForCellExists(cellIdHexString)) {
                val currentMeasurement: Measurement = currentFamm.getMeasurement(cellIdHexString) as Measurement

                // Measurement for cell in received FAMM is newer than the one in current FAMM
                if (currentMeasurement.measurementCreationTimestamp < receivedMeasurement.measurementCreationTimestamp) {
                    currentFamm.setMeasurement(receivedMeasurement)
                }
            }
            // Measurement for cell does not already exist in current FAMM for corresponding client
            else {
                currentFamm.setMeasurement(receivedMeasurement)
            }
        }

        foreignAggregatedMeasurementMaps[senderClientIdHexString] = currentFamm
    }

    // Checked (final)
    /**
     * Generates the LAMM of the client based on its LMM and the FAMMs it has stored
     */
    private fun updateLamm() {
        val newLocalAggregatedMeasurementMap: LocalAggregatedMeasurementMap = LocalAggregatedMeasurementMap()

        // Contains all measurements per cell across the LMM and all FAMMs
        val totalMeasurementsPerCell: MutableMap<String, MutableSet<Measurement>> =
            mutableMapOf<String, MutableSet<Measurement>>()

        localMeasurementMap.getAllMeasurements().forEach { (cellIdHexString, measurement) ->
            totalMeasurementsPerCell[cellIdHexString] = mutableSetOf<Measurement>(measurement)
        }

        foreignAggregatedMeasurementMaps.forEach { (clientIdHexString, famm) ->
            famm.getAllMeasurements().forEach { (cellIdHexString, measurement) ->
                if (totalMeasurementsPerCell.containsKey(cellIdHexString)) {
                    totalMeasurementsPerCell[cellIdHexString]!!.add(measurement)
                } else {
                    totalMeasurementsPerCell[cellIdHexString] = mutableSetOf<Measurement>(measurement)
                }
            }
        }

        totalMeasurementsPerCell.forEach { (cellIdHexString, measurements) ->
            val sortedMeasurements: List<Measurement> = measurements.sortedBy { it.measurementCreationTimestamp }

            // Youngest-Measurement-First as rank function for measurements
            val mostRecentMeasurement: Measurement = sortedMeasurements.last()
            mostRecentMeasurement.clientIdHexString = clientIdHexString
            newLocalAggregatedMeasurementMap.setMeasurement(mostRecentMeasurement)
        }

        localAggregatedMeasurementMap = newLocalAggregatedMeasurementMap
    }

    // Checked (final)
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

    // Checked (final)
    public suspend fun handleReceivedPositionalBeacon(receivedPositionalBeacon: PositionalBeacon): Unit {
        // Lock mutex for exclusive access to and modification of the NT and LMM
        operatingOnDensityDataMutex.withLock {
            updateNtAndLmm(receivedPositionalBeacon)
        }
    }

    // Checked (final)
    public suspend fun handleReceivedFamm(
        senderClientIdHexString: String,
        receivedFamm: ForeignAggregatedMeasurementMap
    ): Unit {
        // Lock mutex for exclusive access to and modification of the corresponding FAMM
        operatingOnDensityDataMutex.withLock {
            updateFamm(senderClientIdHexString, receivedFamm)
        }
    }

    // Checked (final)
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

    // Checked (final)
    public fun getPositionalBeaconFromNtWithLargestTransmissionBenefit(): PositionalBeacon? {
        if (neighborhoodTable.getAllNtEntries().isNotEmpty()) {
            return neighborhoodTable.getAllNtEntries().maxBy { it.value.clientTransmissionBenefit }.value
        }
        return null
    }

    // Checked (final)
    // Remove the entries of the NT, LMM and FAMMs which exceeded the TTL without an external trigger
    public suspend fun removeExceededBeaconsAndMeasurements(): Unit {
        // Lock mutex for exclusive access to and modification of the NT, the LMM and all the FAMMs
        operatingOnDensityDataMutex.withLock {
            val exceededPositionalBeacons: MutableMap<String, PositionalBeacon> =
                neighborhoodTable.getExceededNtEntries(timeToLiveNeighborhoodTableInMilliseconds)

            exceededPositionalBeacons.forEach { (clientIdHexString, positionalBeacon) ->
                neighborhoodTable.removeNtEntry(clientIdHexString)
            }

            localMeasurementMap.removeExceededMmEntries(timeToLiveMeasurementMapInMilliseconds)

            foreignAggregatedMeasurementMaps.forEach { (clientIdHexString, famm) ->
                famm.removeExceededMmEntries(timeToLiveMeasurementMapInMilliseconds)
            }
        }
    }

    // Checked (final)
    // Clear the current LMM and set custom measurements
    // (this is solely for the simulated clients to fill the LMM with the randomized measurements, usual sensing clients
    // shall not use this functionality!)
    public suspend fun clearLmmAndSetNewMeasurements(newMeasurements: List<Measurement>) {
        // Lock mutex for exclusive access to and modification of the LMM
        operatingOnDensityDataMutex.withLock {
            localMeasurementMap.setAllMeasurements(newMeasurements)
        }
    }

    // Checked (final)
    public fun getCurrentLammAndOrderForTransmission(): Pair<LocalAggregatedMeasurementMap, List<String>> {
        return Pair<LocalAggregatedMeasurementMap, List<String>>(
            localAggregatedMeasurementMap,
            orderForMeasurementTransmission
        )
    }

    // Checked (final)
    public fun getCurrentLmm(): LocalMeasurementMap {
        return localMeasurementMap
    }
}