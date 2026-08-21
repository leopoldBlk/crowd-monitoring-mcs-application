package com.leopoldBlk.mobileSensingClients.util.converter

import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.controller.DiscretizationController
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import org.maplibre.spatialk.geojson.GeometryCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import java.math.BigInteger

// Checked (final)
object CellConverter {
    // No initialization at the beginning, because h3-lib is not accessible at the beginning
    private lateinit var h3Core: H3Core

    // Checked (final)
    // Get the vertices of the Uber H3 cell with the given cellId
    // --> Necessary for generating the cell on the map
    private fun getVerticesForUberH3Cell(uberH3CellIdsHexString: String): MutableList<LatLng> {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        val verticesForUberH3Cell: MutableList<LatLng> =
            h3Core.cellAddressesToMultiPolygon(listOf(uberH3CellIdsHexString), true).get(0).get(0)
        return verticesForUberH3Cell
    }

    // Checked (final)
    // Get the vertices of the Uber H3 cell with the given cellId
    // --> Necessary for printing the label of the measurement value within the center of the corresponding map
    private fun getCentrePointForUberH3Cell(uberH3CellIdsHexString: String): LatLng {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        val centrePointForUberH3Cell: LatLng = h3Core.cellToLatLng(uberH3CellIdsHexString)
        return centrePointForUberH3Cell
    }

    // Checked (final)
    // Get the map structures of the local aggregated measurement map, which are printed to the map visible to the user
    // --> Returns triple consisting of
    // 1) Geometry collection of polygons for the cells contained within the LAMM
    // 2) Geometry collection of cells comprising the nearby area of the sensing client
    // 3) Map of the triple (cellId, point of the cell, discretization level of the cell) to the
    // measurement value corresponding to the cell for all the cells contained within the LAMM
    public fun getMapStructuresForLamm(
        localAggregatedMeasurementMap: LocalAggregatedMeasurementMap,
        clientPositionCellId: String
    ): Triple<GeometryCollection<Polygon>, GeometryCollection<Polygon>, MutableMap<Triple<String, Point, Int>, UShort>> {
        val polygonsForLamm: MutableList<Polygon> = mutableListOf<Polygon>()
        val polygonsForNearbyArea: MutableList<Polygon> = mutableListOf<Polygon>()
        val labelsForLammPolygons: MutableMap<Triple<String, Point, Int>, UShort> =
            mutableMapOf<Triple<String, Point, Int>, UShort>()

        val parentCellWithNeighborhoodComprisingNearbyArea: List<String> =
            DiscretizationController.getParentCellWithNeighborhood(
                clientPositionCellId,
                DataStore.levelToDetermineNearbyArea
            )

        parentCellWithNeighborhoodComprisingNearbyArea.forEach { cellId ->
            val verticesForUberH3Cell: MutableList<LatLng> = getVerticesForUberH3Cell(cellId)

            val verticesOfUberH3CellAsPositions: MutableList<Position> =
                mutableListOf<Position>()

            verticesForUberH3Cell.forEach { vertex ->
                val position: Position = Position(vertex.lng, vertex.lat)
                verticesOfUberH3CellAsPositions.add(position)
            }

            val polygon: Polygon = Polygon(verticesOfUberH3CellAsPositions)
            polygonsForNearbyArea.add(polygon)
        }

        localAggregatedMeasurementMap.getAllMeasurements().forEach { (cellId, measurement) ->
            if (measurement.measurementValue > 0u) {
                val verticesForUberH3Cell: MutableList<LatLng> = getVerticesForUberH3Cell(cellId)
                val centrePointForUberH3Cell: LatLng = getCentrePointForUberH3Cell(cellId)

                val verticesOfUberH3CellAsPositions: MutableList<Position> =
                    mutableListOf<Position>()

                verticesForUberH3Cell.forEach { vertex ->
                    val position: Position = Position(vertex.lng, vertex.lat)
                    verticesOfUberH3CellAsPositions.add(position)
                }

                val polygon: Polygon = Polygon(verticesOfUberH3CellAsPositions)
                polygonsForLamm.add(polygon)
                val point: Point = Point(centrePointForUberH3Cell.lng, centrePointForUberH3Cell.lat)
                val cellLevel: Int = h3Core.getResolution(cellId)

                labelsForLammPolygons[Triple(cellId, point, cellLevel)] =
                    measurement.measurementValue
            }
        }

        val polygonGeometryCollection: GeometryCollection<Polygon> =
            GeometryCollection<Polygon>(polygonsForLamm)
        val polygonNearbyAreaGeometryCollection: GeometryCollection<Polygon> =
            GeometryCollection<Polygon>(polygonsForNearbyArea)

        return Triple(
            polygonGeometryCollection,
            polygonNearbyAreaGeometryCollection,
            labelsForLammPolygons
        )
    }

    // Checked (final)
    public fun compressUberH3CellIdHexString(decompressedUberH3CellIdHexString: String): String {
        // Convert hexadecimal string to binary string
        var decompressedUberH3CellIdBinaryString: String =
            BigInteger(decompressedUberH3CellIdHexString, 16).toString(2)

        // Add leading zeros to binary string since these are removed with the conversion using BigInteger(...).toString(...)
        while (decompressedUberH3CellIdBinaryString.length < 64) {
            decompressedUberH3CellIdBinaryString = "0" + decompressedUberH3CellIdBinaryString
        }

        // Remove reserved bits as well as bits indicating cell mode
        var compressedUberH3CellIdBinaryString: String =
            decompressedUberH3CellIdBinaryString.drop(8)

        // Remove the padding bits of digit 13, 14 and 15 as these are not used
        compressedUberH3CellIdBinaryString = compressedUberH3CellIdBinaryString.dropLast(9)

        // Convert binary string to hexadecimal string
        var compressedUberH3CellIdHexString =
            compressedUberH3CellIdBinaryString.toBigInteger(2).toString(16)

        // Add leading zeros to hexadecimal string since these are removed with the conversion using BigInteger(...).toString(...) to fill 6 byte for cell identifier
        while (compressedUberH3CellIdHexString.length < 12) {
            compressedUberH3CellIdHexString = "0" + compressedUberH3CellIdHexString
        }

        return compressedUberH3CellIdHexString
    }

    // Checked (final)
    public fun decompressUberH3CellIdHexString(compressedUberH3CellIdHexString: String): String {
        // Convert hexadecimal string to binary string
        var compressedUberH3CellIdBinaryString: String =
            BigInteger(compressedUberH3CellIdHexString, 16).toString(2)

        // Add leading zeros to binary string since these are removed with the conversion using BigInteger(...).toString(...)
        while (compressedUberH3CellIdBinaryString.length < 47) {
            compressedUberH3CellIdBinaryString = "0" + compressedUberH3CellIdBinaryString
        }

        // Adding reserved bits, bits indicating cell mode and padding bits for digit 13, 14 and 15
        val decompressedUberH3CellIdBinaryString: String =
            "0" + "0001" + "000" + compressedUberH3CellIdBinaryString + "111" + "111" + "111"

        // Convert binary string to hexadecimal string
        var decompressedUberH3CellIdHexString =
            decompressedUberH3CellIdBinaryString.toBigInteger(2).toString(16)

        // Add leading zeros to binary string since these are removed with the conversion using toBigInteger(...).toString(...)
        while (decompressedUberH3CellIdHexString.length < 16) {
            decompressedUberH3CellIdHexString = "0" + decompressedUberH3CellIdHexString
        }

        return decompressedUberH3CellIdHexString
    }

    // Checked (final)
    // Adds leading zeros to cell identifier string, as Uber H3 cuts leading zeros off when generating
    // cell identifiers
    public fun addLeadingZerosToCellId(cellId: String): String {
        var cellIdWithPadding = cellId

        while (cellIdWithPadding.length < 16) {
            cellIdWithPadding = "0" + cellIdWithPadding
        }

        return cellIdWithPadding
    }
}