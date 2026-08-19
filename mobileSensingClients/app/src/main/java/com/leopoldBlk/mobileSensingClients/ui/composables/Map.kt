package com.leopoldBlk.mobileSensingClients.ui.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.leopoldBlk.mobileSensingClients.util.DataStore
import com.leopoldBlk.mobileSensingClients.util.converter.CellConverter
import crowdDensityLibrary.LocalAggregatedMeasurementMap
import crowdDensityLibrary.MeasurementMap
import crowdDensityLibrary.dataClasses.Measurement
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.PositionWithAccuracy
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.toJson
import kotlin.time.TimeSource

@Composable
fun Map(useTestData: Boolean) {
    // Observe client position for update of location puck
    val clientPosition: State<crowdDensityLibrary.dataClasses.Position?> =
        DataStore.clientPosition.observeAsState()

    // Observe local aggregated measurement map for update of fill and symbol layer to show cells with corresponding measurement value
    val lammAndOrderForMeasurementTransmission: State<Pair<MeasurementMap, List<String>>?> =
        DataStore.lammAndOrderForMeasurementTransmission.observeAsState()

    // Local aggregated measurement map to be drawn
    var localAggregatedMeasurementMap: LocalAggregatedMeasurementMap =
        LocalAggregatedMeasurementMap()

    // Only print map if client position is available
    if (clientPosition.value != null) {
        // Sources for realization and implementation of map with its layers and data:
        // https://maplibre.org/maplibre-compose/getting-started/
        // https://maplibre.org/maplibre-gl-js/docs/examples/
        // https://maplibre.org/maplibre-compose/api/lib/maplibre-compose/
        MaplibreMap(
            // Source: https://openfreemap.org
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/positron"),
            // Camera state is not updated in case of recomposition
            cameraState = rememberCameraState(
                CameraPosition(
                    target = Position(
                        clientPosition.value!!.coordinates.longitude,
                        clientPosition.value!!.coordinates.latitude,
                    ),
                    zoom = 17.5
                )
            ),
            options = MapOptions(
                ornamentOptions = OrnamentOptions(
                    padding = PaddingValues(
                        20.dp,
                        60.dp,
                        20.dp,
                        20.dp
                    )
                )
            )
        ) {
            // Draw location puck for the sensing clients location
            // Source: https://maplibre.org/maplibre-compose/api/lib/maplibre-compose/org.maplibre.compose.location/-location-puck.html
            LocationPuck(
                idPrefix = "UserLocation",
                location = Location(
                    position = PositionWithAccuracy(
                        Position(
                            clientPosition.value!!.coordinates.longitude,
                            clientPosition.value!!.coordinates.latitude
                        ), null
                    ),
                    timestamp = TimeSource.Monotonic.markNow()
                ),
                // Camera state is not updated in case of recomposition
                cameraState = rememberCameraState(
                    CameraPosition(
                        target = Position(
                            clientPosition.value!!.coordinates.longitude,
                            clientPosition.value!!.coordinates.latitude
                        ),
                        zoom = 17.5
                    )
                )
            )

            // Fill local aggregated measurement map with test data
            if (useTestData) {
                // Uber H3 level 13 cell
                val firstMeasurement: Measurement = Measurement(
                    "8d1f8d7a498883f",
                    12u,
                    "01020304",
                    // One hour before timestamp starting the test
                    ((System.currentTimeMillis() / 1000) - 3600).toULong()
                )

                // Uber H3 level 13 cell
                val secondMeasurement: Measurement = Measurement(
                    "8d1f8d7a498887f",
                    5u,
                    "01020304",
                    // Two hours before timestamp starting the test
                    ((System.currentTimeMillis() / 1000) - 7200).toULong()
                )

                // Uber H3 level 10 cell
                val thirdMeasurement: Measurement = Measurement(
                    "08a1f8d7a4897fff",
                    78u,
                    "01020304",
                    // Two hours before timestamp starting the test
                    ((System.currentTimeMillis() / 1000) - 7200).toULong()
                )

                // Uber H3 level 10 cell
                val fourthMeasurement: Measurement = Measurement(
                    "08a1f8d7a4c6ffff",
                    94u,
                    "01020304",
                    // Two hours before timestamp starting the test
                    ((System.currentTimeMillis() / 1000) - 7200).toULong()
                )

                localAggregatedMeasurementMap.setMeasurement(firstMeasurement)
                localAggregatedMeasurementMap.setMeasurement(secondMeasurement)
                localAggregatedMeasurementMap.setMeasurement(thirdMeasurement)
                localAggregatedMeasurementMap.setMeasurement(fourthMeasurement)
            }
            // Fill local aggregated measurement map with real data
            else if (lammAndOrderForMeasurementTransmission.value != null) {
                localAggregatedMeasurementMap =
                    lammAndOrderForMeasurementTransmission.value!!.first as LocalAggregatedMeasurementMap
            }

            val mapStructures =
                CellConverter.getMapStructuresForLamm(localAggregatedMeasurementMap, clientPosition.value!!.cellId)

            // Draw Uber H3 cells
            FillLayer(
                id = "UberH3Cells",
                source = rememberGeoJsonSource(GeoJsonData.JsonString(mapStructures.first.toJson())),
                color = const(Color.Red),
                opacity = const(0.2f)
            )

            // Draw nearby area of the mobile sensing client
            LineLayer(
                id = "NearbyArea",
                source = rememberGeoJsonSource(GeoJsonData.JsonString(mapStructures.second.toJson())),
                color = const(Color(43, 145, 251)),
                opacity = const(0.5f)
            )

            var labelCounter: UInt = 0u

            // Draw labels with measurement values for Uber H3 cells
            mapStructures.third.forEach { (cellIdPointLevelTriple, measurementValue) ->
                // Standard zoom level for Uber H3 level 13 cells
                var minZoom: Float = 18.0f

                // Zoom level if cell level is lower than 13
                // --> cell covers larger area and therefore the zoom level when the label starts to be displayed has to be decreased
                if (cellIdPointLevelTriple.third < 13) {
                    minZoom = 14.0f
                }

                SymbolLayer(
                    id = "LabelForCellWithCellId:" + cellIdPointLevelTriple.first,
                    source = rememberGeoJsonSource(GeoJsonData.JsonString(cellIdPointLevelTriple.second.toJson())),
                    textField = const(measurementValue.toString()),
                    textColor = const(
                        Color.Black
                    ),
                    // Sources: https://github.com/maplibre/maplibre-plugins-android/issues/60, https://github.com/hyperknot/openfreemap/issues/10
                    // Bug that symbol layer gets invisible when specifying text field and standard font is not available on the server
                    // --> therefore specify font here explicitly
                    textFont = const(listOf("Noto Sans Regular")),
                    minZoom = minZoom
                )
                labelCounter = labelCounter.inc()
            }
        }
    }
}