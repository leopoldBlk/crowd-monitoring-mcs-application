package com.leopoldBlk.mobileSensingClients.util.converter

import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import org.maplibre.spatialk.geojson.GeometryCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import java.math.BigInteger

object CellConverter {
    // No initialization at the beginning, because h3-lib is not accessible at the beginning
    private lateinit var h3Core: H3Core

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

    // Get the vertices of the Uber H3 cell with the given cellId
    // --> Necessary for printing the label of the measurement value within the center of the corresponding map
    private fun getCentrePointForUberH3Cell(uberH3CellIdsHexString: String): LatLng {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        val centrePointForUberH3Cell: LatLng = h3Core.cellToLatLng(uberH3CellIdsHexString)
        return centrePointForUberH3Cell
    }

    // Returns the list of Uber H3 cellIds which are defined as proximate area as hexadecimal strings
    private fun getProximateParentCellAndNeighborhoodCells(
        clientPositionCellIdHexString: String,
        cellLevelProximate: UShort
    ): List<String> {
        // Get cellId of parent cell the client is located in
        val clientPositionProximateParentCellIdHexString: String =
            h3Core.cellToParentAddress(clientPositionCellIdHexString, cellLevelProximate.toInt())
        // Get cellIds of the neighboring cells surrounding the parent cell
        val clientPositionProximateParentNeighborhoodCellIdsHexString: List<String> =
            h3Core.gridRing(clientPositionProximateParentCellIdHexString, 1)

        return listOf<String>(clientPositionProximateParentCellIdHexString).plus(
            clientPositionProximateParentNeighborhoodCellIdsHexString
        )
    }

    // This shall be called, if a lamm is received from a server
    public fun convertDistantCells(localAggregatedMeasurementMap: LocalAggregatedMeasurementMap): LocalAggregatedMeasurementMap {
        if (!::h3Core.isInitialized) {
            h3Core = H3Core.newSystemInstance()
        }

        val copyOfLocalAggregatedMeasurementMap: LocalAggregatedMeasurementMap =
            LocalAggregatedMeasurementMap()

        // Copy to not change the given lamm
        localAggregatedMeasurementMap.getAllMeasurements().forEach { (cellId, measurement) ->
            copyOfLocalAggregatedMeasurementMap.setMeasurement(measurement)
        }

        // Cell level for parent cell and its surrounding neighbors which are defined as proximate area
        val cellLevelToDetermineProximity: UShort = 10u
        val clientPosition: crowdDensityLibrary.dataClasses.Position? =
            DataStore.clientPosition.value

        // For testing the handling of distant cells:
        // val clientPosition = 1

        if (clientPosition != null) {
            // Get the list of Uber H3 cellIds which are defined as proximate area as hexadecimal strings
            val proximateParentAndNeighborhoodCellIdsHexString: List<String> =
                getProximateParentCellAndNeighborhoodCells(
                    clientPosition.cellId,
                    // For testing the handling of distant cells:
                    // "08d1f8d7a498c0bf",
                    cellLevelToDetermineProximity
                )

            // List of cellIds for which the measurement has to be removed from the lamm because it has been converted to a lower level (larger cell)
            val cellIdsToRemoveMeasurementFor: MutableList<String> = mutableListOf<String>()
            // List of new measurements resulting from the conversion process
            val convertedCellMeasurements: MutableMap<String, Measurement> =
                mutableMapOf<String, Measurement>()

            copyOfLocalAggregatedMeasurementMap.getAllMeasurements()
                .forEach { (cellId, measurement) ->
                    val parentCellId: String =
                        h3Core.cellToParentAddress(cellId, cellLevelToDetermineProximity.toInt())

                    // The parent cell of the cell the measurement belongs to is not within the proximate area
                    // --> use lower discretization level (equal to cell with larger coverage are) for this area
                    if (!proximateParentAndNeighborhoodCellIdsHexString.contains(parentCellId)) {
                        // If there is no measurement for the corresponding parent cell
                        if (!convertedCellMeasurements.containsKey(parentCellId)) {
                            val measurement: Measurement = Measurement(
                                parentCellId,
                                measurement.measurementValue,
                                measurement.clientId,
                                measurement.measurementCreationTimestamp
                            )

                            convertedCellMeasurements.set(parentCellId, measurement)
                        }
                        // If there is already a measurement for the corresponding parent cell
                        // --> update the measurement (sum measurement values and take the newer measurement timestamp)
                        // --> will lead to overestimation of density but safer than underestimation in this case and density doesnt have to be that accurate for distant areas
                        else {
                            val existingMeasurement: Measurement =
                                convertedCellMeasurements.get(parentCellId)!!

                            val newMeasurement: Measurement = Measurement(
                                parentCellId,
                                (existingMeasurement.measurementValue + measurement.measurementValue).toUShort(),
                                existingMeasurement.clientId,
                                if (existingMeasurement.measurementCreationTimestamp < measurement.measurementCreationTimestamp) measurement.measurementCreationTimestamp.toULong() else existingMeasurement.measurementCreationTimestamp.toULong()
                            )

                            convertedCellMeasurements.set(parentCellId, newMeasurement)
                        }

                        cellIdsToRemoveMeasurementFor.add(cellId)
                    }
                }

            // Remove the cells from lamm which have been converted into cell with lower level
            cellIdsToRemoveMeasurementFor.forEach { cellIdsToRemoveMeasurementFor ->
                copyOfLocalAggregatedMeasurementMap.removeMeasurementForCellId(
                    cellIdsToRemoveMeasurementFor
                )
            }

            // Insert the newly generated cells due to conversion into cells with lower level into the lamm
            convertedCellMeasurements.forEach { (cellId, measurement) ->
                copyOfLocalAggregatedMeasurementMap.setMeasurement(measurement)
            }
        }

        return copyOfLocalAggregatedMeasurementMap
    }

    // Get the map structures of the local aggregated measurement map, which are printed to the map visible to the user
    // --> Returns geometry collection of polygons for the cells and a map of the cells centroids as points to the measurement value of the corresponding
    // cell for printing the measurement value to the map visible to the user
    public fun getMapStructuresForLamm(localAggregatedMeasurementMap: LocalAggregatedMeasurementMap): Pair<GeometryCollection<Polygon>, MutableMap<Triple<String, Point, Int>, UShort>> {
        val polygonsForUberH3Cells: MutableList<Polygon> = mutableListOf<Polygon>()
        val labelsForUberH3Cells: MutableMap<Triple<String, Point, Int>, UShort> =
            mutableMapOf<Triple<String, Point, Int>, UShort>()

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
                polygonsForUberH3Cells.add(polygon)
                val point: Point = Point(centrePointForUberH3Cell.lng, centrePointForUberH3Cell.lat)
                val cellLevel: Int = h3Core.getResolution(cellId)

                labelsForUberH3Cells[Triple(cellId, point, cellLevel)] =
                    measurement.measurementValue
            }
        }

        val polygonGeometryCollection: GeometryCollection<Polygon> =
            GeometryCollection<Polygon>(polygonsForUberH3Cells)

        return Pair(polygonGeometryCollection, labelsForUberH3Cells)
    }

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

        // Remove the padding bits of digit 14 and 15 as these are not used
        compressedUberH3CellIdBinaryString = compressedUberH3CellIdBinaryString.dropLast(6)

        // Convert binary string to hexadecimal string
        var compressedUberH3CellIdHexString =
            compressedUberH3CellIdBinaryString.toBigInteger(2).toString(16)

        // Add leading zeros to hexadecimal string since these are removed with the conversion using BigInteger(...).toString(...) to fill 7 byte for cell identifier
        while (compressedUberH3CellIdHexString.length < 14) {
            compressedUberH3CellIdHexString = "0" + compressedUberH3CellIdHexString
        }

        return compressedUberH3CellIdHexString
    }

    public fun decompressUberH3CellIdHexString(compressedUberH3CellIdHexString: String): String {
        // Convert hexadecimal string to binary string
        var compressedUberH3CellIdBinaryString: String =
            BigInteger(compressedUberH3CellIdHexString, 16).toString(2)

        // Add leading zeros to binary string since these are removed with the conversion using BigInteger(...).toString(...)
        while (compressedUberH3CellIdBinaryString.length < 50) {
            compressedUberH3CellIdBinaryString = "0" + compressedUberH3CellIdBinaryString
        }

        // Adding reserved bits, bits indicating cell mode and padding bits for digit 14 and 15
        val decompressedUberH3CellIdBinaryString: String =
            "0" + "0001" + "000" + compressedUberH3CellIdBinaryString + "111" + "111"

        // Convert binary string to hexadecimal string
        var decompressedUberH3CellIdHexString =
            decompressedUberH3CellIdBinaryString.toBigInteger(2).toString(16)

        // Add leading zeros to binary string since these are removed with the conversion using toBigInteger(...).toString(...)
        while (decompressedUberH3CellIdHexString.length < 16) {
            decompressedUberH3CellIdHexString = "0" + decompressedUberH3CellIdHexString
        }

        return decompressedUberH3CellIdHexString
    }

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