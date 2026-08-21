package com.leopoldBlk.mobileSensingClients

import com.leopoldBlk.mobileSensingClients.services.wifiAware.positionalBeacon.PositionalBeaconTO
import com.leopoldBlk.mobileSensingClients.util.converter.PositionalBeaconConverter
import crowdDensityLibrary.dataClasses.Coordinates
import crowdDensityLibrary.dataClasses.Position
import crowdDensityLibrary.dataClasses.PositionalBeacon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PositionalBeaconConverterTest {
    private val positionalBeaconForTest: PositionalBeacon = PositionalBeacon(
        "01020304",
        Position(Coordinates(48.137371, 11.575328), "08c1f8d7a49883ff"),
        50.0f,
        Instant.parse("2026-01-01T11:12:13Z").epochSecond.toULong()
    )

    private lateinit var positionalBeaconForTestConvertedToPositionalBeaconTO: PositionalBeaconTO
    private lateinit var positionalBeaconToForTestConvertedToByteArray: ByteArray
    private lateinit var byteArrayForTestReconvertedToPositionalBeaconTO: PositionalBeaconTO
    private lateinit var positionalBeaconTOReconvertedToPositionalBeacon: PositionalBeacon

    @Test
    public fun positionalBeaconConverterTest() {
        positionalBeaconForTestConvertedToPositionalBeaconTO =
            PositionalBeaconConverter.convertPositionalBeaconToPositionalBeaconTO(
                positionalBeaconForTest
            )
        positionalBeaconToForTestConvertedToByteArray =
            positionalBeaconForTestConvertedToPositionalBeaconTO.getPositionalBeaconToAsByteArray()

        byteArrayForTestReconvertedToPositionalBeaconTO =
            PositionalBeaconConverter.convertByteArrayToPositionalBeaconTO(
                positionalBeaconToForTestConvertedToByteArray
            )
        positionalBeaconTOReconvertedToPositionalBeacon =
            PositionalBeaconConverter.convertPositionalBeaconToToPositionalBeacon(
                byteArrayForTestReconvertedToPositionalBeaconTO
            )

        assertTrue(positionalBeaconForTest.clientIdHexString contentEquals positionalBeaconTOReconvertedToPositionalBeacon.clientIdHexString)
        assertEquals(
            positionalBeaconForTest.clientPosition.coordinates.latitude,
            positionalBeaconTOReconvertedToPositionalBeacon.clientPosition.coordinates.latitude,
            0.0
        )
        assertEquals(
            positionalBeaconForTest.clientPosition.coordinates.longitude,
            positionalBeaconTOReconvertedToPositionalBeacon.clientPosition.coordinates.longitude,
            0.0
        )
        assertEquals(
            positionalBeaconForTest.clientPosition.cellIdHexString,
            positionalBeaconTOReconvertedToPositionalBeacon.clientPosition.cellIdHexString
        )
        assertEquals(
            positionalBeaconForTest.clientTransmissionBenefit,
            positionalBeaconTOReconvertedToPositionalBeacon.clientTransmissionBenefit,
            0.0f
        )
        assertEquals(
            positionalBeaconForTest.beaconCreationTimestamp,
            positionalBeaconTOReconvertedToPositionalBeacon.beaconCreationTimestamp
        )
    }
}