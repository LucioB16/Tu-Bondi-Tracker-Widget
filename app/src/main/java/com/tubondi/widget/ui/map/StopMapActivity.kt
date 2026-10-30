package com.tubondi.widget.ui.map

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.tubondi.widget.ui.theme.TuBondiTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Serializable
data class MapStopPayload(
    /** Internal only: used to return a selection to the configuration draft. */
    val code: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val selectable: Boolean = true,
    val selected: Boolean = false
)

class StopMapActivity : ComponentActivity() {
    private lateinit var stops: List<MapStopPayload>
    private var mapView: MapView? = null
    private var mapMessage by mutableStateOf<String?>(null)
    private var selectedCodes by mutableStateOf<Set<String>>(emptySet())
    private var activeStopCode by mutableStateOf<String?>(null)
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MapLibre.getInstance(applicationContext)
        stops = runCatching {
            val encoded = intent.getStringExtra(EXTRA_STOPS).orEmpty()
            json.decodeFromString(ListSerializer(MapStopPayload.serializer()), encoded)
        }.getOrDefault(emptyList())
        selectedCodes = stops.filter { it.selectable && it.selected }.mapTo(mutableSetOf()) { it.code }

        val locatedStops = stops.withIndex().filter { it.value.hasValidCoordinates() }
        if (locatedStops.isEmpty()) {
            mapMessage = "No hay coordenadas disponibles para estas paradas."
        } else {
            mapView = MapView(this).also { view ->
                view.onCreate(savedInstanceState)
                setupMap(view, locatedStops)
            }
        }

        setContent {
            TuBondiTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Paradas en mapa") },
                            actions = {
                                androidx.compose.material3.TextButton(onClick = ::finishWithSelection) {
                                    Text(if (stops.any { it.selectable }) "Listo" else "Cerrar")
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = ::finish,
                                    modifier = Modifier.semantics { contentDescription = "Volver" }
                                ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        mapMessage?.let { message ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(14.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        mapView?.let { view ->
                            AndroidView(
                                factory = { view },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                                    .semantics { contentDescription = "Mapa con las paradas seleccionadas" }
                            )
                            Text(
                                text = "© OpenStreetMap contributors · OpenMapTiles",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        activeStopCode?.let { code ->
                            stops.firstOrNull { it.code == code }?.let { stop ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stop.name,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (stop.selectable) {
                                            OutlinedButton(
                                                onClick = { toggleStop(stop) },
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Text(if (code in selectedCodes) "Quitar" else "Agregar")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = if (mapView == null) 560.dp else 190.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(stops.size) { index ->
                                val stop = stops[index]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        androidx.compose.material3.TextButton(
                                            onClick = { activeStopCode = stop.code },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        ) {
                                            Text(
                                                text = "${index + 1}. ${stop.name}",
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        if (!stop.hasValidCoordinates()) {
                                            Text(
                                                text = "Sin ubicación en el mapa",
                                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                    if (stop.selectable) {
                                        OutlinedButton(
                                            onClick = { toggleStop(stop) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(if (stop.code in selectedCodes) "Quitar" else "Agregar")
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.heightIn(min = 8.dp))
                    }
                }
            }
        }
    }

    private fun setupMap(view: MapView, locatedStops: List<IndexedValue<MapStopPayload>>) {
        view.addOnDidFailLoadingMapListener {
            runOnUiThread { mapMessage = "No se pudo cargar el mapa. La lista de paradas sigue disponible." }
        }
        view.addOnDidFinishLoadingMapListener {
            runOnUiThread { mapMessage = null }
        }
        view.getMapAsync { map ->
            map.setStyle(OPENFREEMAP_STYLE) { _ ->
                val points = locatedStops.map {
                    LatLng(it.value.latitude!!, it.value.longitude!!)
                }
                val markerStops = mutableMapOf<Long, List<IndexedValue<MapStopPayload>>>()
                val iconFactory = IconFactory.getInstance(applicationContext)
                val gridSizePx = (72 * resources.displayMetrics.density).toInt().coerceAtLeast(1)

                fun drawMarkers() {
                    map.removeAnnotations()
                    markerStops.clear()
                    val buckets = linkedMapOf<Pair<Int, Int>, MutableList<IndexedValue<MapStopPayload>>>()
                    locatedStops.forEach { indexedStop ->
                        val point = LatLng(indexedStop.value.latitude!!, indexedStop.value.longitude!!)
                        val screenPoint = map.projection.toScreenLocation(point)
                        val key = (screenPoint.x / gridSizePx).toInt() to (screenPoint.y / gridSizePx).toInt()
                        buckets.getOrPut(key) { mutableListOf() }.add(indexedStop)
                    }
                    buckets.values.forEach { group ->
                        val label = if (group.size == 1) (group.first().index + 1).toString() else group.size.toString()
                        val position = LatLng(
                            group.map { it.value.latitude!! }.average(),
                            group.map { it.value.longitude!! }.average()
                        )
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(if (group.size == 1) group.first().value.name else "${group.size} paradas")
                                .icon(iconFactory.fromBitmap(createStopMarkerBitmap(label, isCluster = group.size > 1)))
                        )
                        markerStops[marker.id] = group
                    }
                }

                map.addOnCameraIdleListener { drawMarkers() }
                map.setOnMarkerClickListener { marker ->
                    val group = markerStops[marker.id].orEmpty()
                    if (group.size == 1) {
                        activeStopCode = group.first().value.code
                    } else if (group.isNotEmpty()) {
                        activeStopCode = null
                        val groupPoints = group.map {
                            LatLng(it.value.latitude!!, it.value.longitude!!)
                        }.distinct()
                        if (map.cameraPosition.zoom >= 17.5) {
                            activeStopCode = group.first().value.code
                        } else if (groupPoints.size > 1) {
                            val bounds = LatLngBounds.Builder().apply { groupPoints.forEach(::include) }.build()
                            val insetPx = (48 * resources.displayMetrics.density).toInt()
                            runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, insetPx)) }
                                .onFailure {
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            marker.position,
                                            (map.cameraPosition.zoom + 2).coerceAtMost(17.5)
                                        )
                                    )
                                }
                        } else {
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    marker.position,
                                    (map.cameraPosition.zoom + 2).coerceAtMost(17.5)
                                )
                            )
                        }
                    }
                    true
                }
                map.addOnMapClickListener {
                    activeStopCode = null
                    false
                }
                val center = LatLng(points.map { it.latitude }.average(), points.map { it.longitude }.average())
                if (points.distinct().size < 2) {
                    map.cameraPosition = CameraPosition.Builder().target(center).zoom(15.0).build()
                    view.post { drawMarkers() }
                } else {
                    val bounds = LatLngBounds.Builder().apply { points.forEach(::include) }.build()
                    view.post {
                        val insetPx = (36 * resources.displayMetrics.density).toInt()
                        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, insetPx)) }
                            .onFailure {
                                map.cameraPosition = CameraPosition.Builder().target(center).zoom(11.0).build()
                                drawMarkers()
                            }
                    }
                }
            }
        }
    }

    private fun createStopMarkerBitmap(label: String, isCluster: Boolean): Bitmap {
        val density = resources.displayMetrics.density
        val size = ((if (isCluster) 34 else 24) * density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isCluster) android.graphics.Color.rgb(0, 83, 79) else android.graphics.Color.rgb(0, 107, 102)
            style = Paint.Style.FILL
        }
        val center = size / 2f
        val radius = center - stroke.strokeWidth
        canvas.drawCircle(center, center, radius, fill)
        canvas.drawCircle(center, center, radius, stroke)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * when {
                label.length >= 3 -> 0.30f
                label.length == 2 -> 0.37f
                else -> 0.44f
            }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, center, center - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint)
        return bitmap
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { mapView?.onPause(); super.onPause() }
    override fun onStop() { mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroy() { mapView?.onDestroy(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun toggleStop(stop: MapStopPayload) {
        if (!stop.selectable) return
        selectedCodes = if (stop.code in selectedCodes) selectedCodes - stop.code else selectedCodes + stop.code
    }

    private fun finishWithSelection() {
        if (stops.any { it.selectable }) {
            setResult(
                RESULT_OK,
                android.content.Intent().putStringArrayListExtra(EXTRA_SELECTED_CODES, ArrayList(selectedCodes))
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_STOPS = "com.tubondi.widget.extra.MAP_STOPS"
        const val EXTRA_SELECTED_CODES = "com.tubondi.widget.extra.MAP_SELECTED_CODES"
        private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    }
}

internal fun MapStopPayload.hasValidCoordinates(): Boolean =
    latitude?.isFinite() == true && longitude?.isFinite() == true &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
