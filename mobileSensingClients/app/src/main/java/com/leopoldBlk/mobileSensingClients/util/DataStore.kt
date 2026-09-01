package com.leopoldBlk.mobileSensingClients.util

import androidx.lifecycle.MutableLiveData
import com.google.android.gms.time.TrustedTimeClient
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementManager
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon
import kotlin.random.Random

object DataStore {
    public var isGeneralFunctionalityEvaluation = false

    public var clientId: ByteArray = Random.nextBytes(ByteArray(4))
    public var clientIdHexString: String = clientId.toHexString()

    // TTL for NT and MMs
    public val timeToLiveNeighborhoodTableInMilliseconds: ULong = 10000u
    public val timeToLiveMeasurementMapInMilliseconds: ULong = 60000u

    public val publishLammIntervalInMilliseconds: ULong = 5000u
    public val lowerLimitForPublishingIntervalPerDataPacketInMilliseconds: ULong = 500u
    public val publishPositionalBeaconIntervalInMilliseconds: ULong = 5000u

    public var accuracyThresholdForLocationDeterminationInMeters: Float = 10.0f

    // Uber H3 cell levels used for discretizing nearby area, discretizing distant area and to determine the nearby
    // area of the client
    public val nearbyAreaDiscretizationLevel: UShort = 12U
    public val distantAreaDiscretizationLevel: UShort = 11U
    public val levelToDetermineNearbyArea: UShort = 11U

    public val transmissionBenefitMultiplicationFaktorWifi: Float = 2.817f
    public val transmissionBenefitMultiplicationFaktorCellular: Float = 1.0f
    public val transmissionBenefitMultiplicationFaktorNoConnection: Float = 0.0f

    public var trustedTimeClient: TrustedTimeClient? = null

    public var measurementManager: MeasurementManager =
        MeasurementManager(
            clientIdHexString,
            timeToLiveNeighborhoodTableInMilliseconds,
            timeToLiveMeasurementMapInMilliseconds
        )

    public var clientPosition: MutableLiveData<Position> =
        MutableLiveData(null)
    public var clientTransmissionBenefit: MutableLiveData<Float?> =
        MutableLiveData(null)
    public var clientPositionalBeacon: MutableLiveData<PositionalBeacon?> = MutableLiveData(null)

    // By default, each mobile sensing client does not act as an edge client
    public var isEdgeClient: MutableLiveData<Boolean> =
        MutableLiveData(false)

    // TODO: Get initial lamm and order for transmission from measurement manager instead of creating here
    public var lammAndOrderForMeasurementTransmission: MutableLiveData<Triple<MeasurementMap, List<String>, Long?>> =
        MutableLiveData(Triple(LocalAggregatedMeasurementMap(), mutableListOf<String>(), null))

    public fun updateClientId(newClientId: ByteArray) {
        clientId = newClientId
        clientIdHexString = newClientId.toHexString()

        measurementManager = MeasurementManager(
            clientIdHexString,
            timeToLiveNeighborhoodTableInMilliseconds,
            timeToLiveMeasurementMapInMilliseconds
        )
    }
}