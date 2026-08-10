package com.leopoldBlk.mobileSensingClients

import com.leopoldBlk.mobileSensingClients.util.converter.CellConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class CellConverterTest {
    private val uberH3CellIdHexStringForTest: String = "08c1f8d7a4988bff"

    @Test
    public fun cellIdConverterTest() {
        val compressedUberH3CellIdHexString =
            CellConverter.compressUberH3CellIdHexString(uberH3CellIdHexStringForTest)
        val decompressedUberH3CellIdHexString =
            CellConverter.decompressUberH3CellIdHexString(compressedUberH3CellIdHexString)

        assertEquals(uberH3CellIdHexStringForTest, decompressedUberH3CellIdHexString)
    }
}