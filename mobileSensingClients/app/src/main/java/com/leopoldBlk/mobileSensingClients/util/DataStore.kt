package com.leopoldBlk.mobileSensingClients.util

import androidx.lifecycle.MutableLiveData
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementManager
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlin.random.Random

object DataStore {
    public var clientId: ByteArray = Random.nextBytes(ByteArray(4))
    public var clientIdHexString: String = clientId.toHexString()

    public val timeToLiveInMinutes: ULong = 2u
    public val timeToLiveInSeconds: ULong = 60u
    public val publishLammIntervalInMilliseconds: ULong = 5000u

    // Uber H3 cell levels used for discretizing nearby area, discretizing distant area and to determine the nearby
    // area of the client
    public val nearbyAreaDiscretizationLevel: UShort = 12U
    public val distantAreaDiscretizationLevel: UShort = 11U
    public val levelToDetermineNearbyArea: UShort = 11U

    public var measurementManager: MeasurementManager =
        MeasurementManager(clientIdHexString, timeToLiveInSeconds)

    public var clientPosition: MutableLiveData<Position> =
        MutableLiveData(null)
    public var clientTransmissionBenefit: MutableLiveData<Float?> =
        MutableLiveData(null)
    // By default, each mobile sensing client does not act as an edge client
    public var clientHasLargestTransmissionBenefitOfNearbyClients: MutableLiveData<Boolean> =
        MutableLiveData(false)
    public var clientPositionalBeacon: MutableLiveData<PositionalBeacon?> = MutableLiveData(null)

    // TODO: Get initial lamm and order for transmission from measurement manager instead of creating here
    public var lammAndOrderForMeasurementTransmission: MutableLiveData<Pair<MeasurementMap, List<String>>> =
        MutableLiveData(Pair(LocalAggregatedMeasurementMap(), mutableListOf<String>()))

    public fun updateClientId(newClientId: ByteArray) {
        clientId = newClientId
        clientIdHexString = newClientId.toHexString()

        measurementManager = MeasurementManager(clientIdHexString, timeToLiveInSeconds)
    }
}