package com.leopoldBlk.mobileSensingClients.util

import androidx.lifecycle.MutableLiveData
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementManager
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlin.random.Random

object DataStore {
    val clientId: ByteArray = Random.nextBytes(ByteArray(4))
    val clientIdHexString: String = clientId.toHexString()

    val timeToLiveInMinutes: ULong = 2u
    val timeToLiveInSeconds: ULong = 60u

    val publishLammIntervalInMilliseconds: ULong = 3000u

    val measurementManager: MeasurementManager =
        MeasurementManager(clientIdHexString, timeToLiveInSeconds)

    var clientPosition: MutableLiveData<Position> =
        MutableLiveData(null)
    var clientTransmissionBenefit: MutableLiveData<Float?> =
        MutableLiveData(null)
    var clientHasLargestTransmissionBenefitOfNearbyClients: MutableLiveData<Boolean> =
        MutableLiveData(true)
    var clientPositionalBeacon: MutableLiveData<PositionalBeacon?> = MutableLiveData(null)

    // TODO: Get initial lamm and order for transmission from measurement manager instead of creating here
    var lammAndOrderForMeasurementTransmission: MutableLiveData<Pair<MeasurementMap, List<String>>> =
        MutableLiveData(Pair(LocalAggregatedMeasurementMap(), mutableListOf<String>()))
}