package com.leopoldBlk.mobileSensingClients

import com.leopoldBlk.mobileSensingClients.util.CellConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class CellConverterTest {
    private val uberH3CellIdHexStringForTest: String = "08d1f8d7a498883f"

    @Test
    public fun cellIdConverterTest() {
        val compressedUberH3CellIdHexString =
            CellConverter.compressUberH3CellIdHexString(uberH3CellIdHexStringForTest)
        val decompressedUberH3CellIdHexString =
            CellConverter.decompressUberH3CellIdHexString(compressedUberH3CellIdHexString)

        assertEquals(uberH3CellIdHexStringForTest, decompressedUberH3CellIdHexString)
    }
}