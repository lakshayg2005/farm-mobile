package com.farm.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** What a [MapPin] marks; each kind has its own icon so the map reads at a glance. */
enum class PinKind {
    /** A plain tinted pin (farms, plots). */
    DEFAULT,

    /** The person's own position: a blue dot with a white ring. */
    ME,

    /** Where goods are collected: a green pin with a box. */
    PICKUP,

    /** Where goods are delivered: an orange pin with a house. */
    DROP,
}

/** One pin on a [TileMap]. [badge] (e.g. the stop number) is drawn in a small circle on the pin. */
data class MapPin(
    val key: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val snippet: String? = null,
    val color: Color = FarmColors.Primary,
    val kind: PinKind = PinKind.DEFAULT,
    val badge: String? = null,
    /** A finished stop: drawn grey. */
    val muted: Boolean = false,
)

/** Colours of the delivery pins, shared with legends and stop cards so they match the map. */
object PinColors {
    val Me = Color(0xFF2F5FD0)
    val Pickup = Color(0xFF1D7A4D)
    val Drop = Color(0xFFD9730D)
    val Muted = Color(0xFF9AA1A7)
    val Route = Color(0xFF3A4249)
}

/** Key of the "you are here" pin in [TileMap]; tapping it selects nothing. */
const val ME_PIN_KEY = "__me"

/** The person's own position as a blue dot (title "You are here", or the place they chose). */
fun mePin(lat: Double, lng: Double, title: String = "You are here") =
    MapPin(ME_PIN_KEY, lat, lng, title, color = PinColors.Me, kind = PinKind.ME)

/** A blue "you are here" dot for osmdroid maps that draw their own overlays. */
fun meMarker(map: MapView, lat: Double, lng: Double, title: String = "You are here"): Marker = Marker(map).apply {
    position = GeoPoint(lat, lng)
    this.title = title
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    pinIcon(map.context, mePin(lat, lng, title), selected = false)?.let { icon = it }
    setOnMarkerClickListener { m, _ -> m.showInfoWindow(); true }
}

private val iconCache = HashMap<String, Drawable>()

/**
 * The marker icon for [pin]: a blue dot for [PinKind.ME], a coloured teardrop with a parcel / house for
 * pickups and drops (plus the [MapPin.badge] number), and the tinted default pin otherwise.
 */
fun pinIcon(context: Context, pin: MapPin, selected: Boolean): Drawable? {
    if (pin.kind == PinKind.DEFAULT) {
        return ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
            setTint((if (selected) FarmColors.Info else pin.color).toArgb())
        }
    }
    val cacheKey = "${pin.kind}|${pin.badge}|${pin.muted}|$selected"
    iconCache[cacheKey]?.let { return it }
    val density = context.resources.displayMetrics.density
    val bmp = if (pin.kind == PinKind.ME) drawMeDot(density) else drawStopPin(density, pin, selected)
    return BitmapDrawable(context.resources, bmp).also { iconCache[cacheKey] = it }
}

private fun drawMeDot(d: Float): Bitmap {
    val size = (30 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = PinColors.Me.copy(alpha = 0.22f).toArgb()
    c.drawCircle(cx, cx, cx, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cx, 9 * d, p)
    p.color = PinColors.Me.toArgb()
    c.drawCircle(cx, cx, 6.5f * d, p)
    return bmp
}

private fun drawStopPin(d: Float, pin: MapPin, selected: Boolean): Bitmap {
    val scale = if (selected) 1.2f else 1f
    val w = 34 * d * scale
    val h = 44 * d * scale
    val badgeRoom = 8 * d * scale
    val bmp = Bitmap.createBitmap((w + badgeRoom).toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val color = when {
        pin.muted -> PinColors.Muted
        pin.kind == PinKind.PICKUP -> PinColors.Pickup
        else -> PinColors.Drop
    }
    val r = w / 2f
    // Centred in the bitmap so the bottom-centre anchor sits on the point of the pin.
    val cx = bmp.width / 2f
    val cy = r
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // Teardrop: a circle with a point at the bottom.
    val tail = Path().apply {
        moveTo(cx, h)
        lineTo(cx - r * 0.62f, cy + r * 0.78f)
        lineTo(cx + r * 0.62f, cy + r * 0.78f)
        close()
    }
    p.color = color.toArgb()
    c.drawPath(tail, p)
    c.drawCircle(cx, cy, r, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, r * 0.72f, p)
    drawGlyph(c, pin.kind, cx, cy, r * 1.05f, color.toArgb())
    pin.badge?.let { b ->
        val br = 8.5f * d * scale
        val bx = bmp.width - br - 1.5f * d
        val by = br + 1.5f * d
        p.color = android.graphics.Color.WHITE
        c.drawCircle(bx, by, br + 1.5f * d, p)
        p.color = PinColors.Route.toArgb()
        c.drawCircle(bx, by, br, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = br * 1.15f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(b, bx, by - (t.descent() + t.ascent()) / 2f, t)
    }
    return bmp
}

// Glyphs on a 24 x 24 grid (Material style): a house for drops, a parcel for pickups.
private const val HOUSE = "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"
private const val PARCEL = "M12,2.5L20.5,7v10L12,21.5 3.5,17V7z"
private const val PARCEL_LINES = "M3.5,7L12,11.5 20.5,7M12,11.5V21.5M7.75,4.75L16.25,9.25"

private fun scaled(data: String, cx: Float, cy: Float, size: Float): Path {
    val p = androidx.core.graphics.PathParser.createPathFromPathData(data)
    val s = size / 24f
    p.transform(android.graphics.Matrix().apply { setScale(s, s); postTranslate(cx - 12 * s, cy - 12 * s) })
    return p
}

/** The pin's glyph in [color], centred on ([cx], [cy]) and [size] wide. */
private fun drawGlyph(c: Canvas, kind: PinKind, cx: Float, cy: Float, size: Float, color: Int) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    if (kind == PinKind.PICKUP) {
        c.drawPath(scaled(PARCEL, cx, cy, size), fill)
        val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size / 24f * 1.6f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        c.drawPath(scaled(PARCEL_LINES, cx, cy, size), lines)
    } else {
        c.drawPath(scaled(HOUSE, cx, cy, size), fill)
    }
}

/** osmdroid needs an identifying user agent (OSM tile policy) and a writable cache. */
fun configureOsm(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
}

/**
 * OpenStreetMap tiles (osmdroid, no API key) with [pins] as markers and, optionally, a [path]
 * drawn through points in order and a dashed [leadIn] (e.g. from the driver to the first stop).
 * On first show and whenever the set of pins changes, the map fits them all. Tapping a pin calls
 * [onSelect]. With [interactive] off the map is a still preview: no panning, zooming or taps.
 */
@Composable
fun TileMap(
    pins: List<MapPin>,
    centerLat: Double,
    centerLng: Double,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    onSelect: (String) -> Unit = {},
    path: List<Pair<Double, Double>> = emptyList(),
    leadIn: List<Pair<Double, Double>> = emptyList(),
    interactive: Boolean = true,
    description: String = "Map",
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val select by rememberUpdatedState(onSelect)
    val mapView = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(
                if (interactive) CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
                else CustomZoomButtonsController.Visibility.NEVER,
            )
            // A preview swallows touches so it neither pans nor reacts to a scroll of the list.
            if (!interactive) setOnTouchListener { _, _ -> true }
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(centerLat, centerLng))
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Fit the pins when the set of pins changes (not on every selection).
    val pinKeys = pins.map { it.key }
    LaunchedEffect(pinKeys, centerLat, centerLng) {
        val points = pins.map { GeoPoint(it.lat, it.lng) }
        when {
            points.size >= 2 -> mapView.post {
                runCatching { mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.35f), false) }
            }
            points.size == 1 -> mapView.controller.animateTo(points[0])
            else -> mapView.controller.animateTo(GeoPoint(centerLat, centerLng))
        }
    }

    Box(modifier.semantics { contentDescription = description }) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.removeAll { it is Marker || it is Polyline }
                val density = map.context.resources.displayMetrics.density
                if (leadIn.size >= 2) {
                    map.overlays.add(
                        Polyline(map).apply {
                            setPoints(leadIn.map { GeoPoint(it.first, it.second) })
                            outlinePaint.color = PinColors.Me.toArgb()
                            outlinePaint.strokeWidth = 3f * density
                            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
                        },
                    )
                }
                if (path.size >= 2) {
                    map.overlays.add(
                        Polyline(map).apply {
                            setPoints(path.map { GeoPoint(it.first, it.second) })
                            outlinePaint.color = PinColors.Route.toArgb()
                            outlinePaint.strokeWidth = 4f * density
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                        },
                    )
                }
                // Your own dot is drawn last so stops never hide it.
                pins.sortedBy { it.kind == PinKind.ME }.forEach { pin ->
                    val selected = pin.key == selectedKey
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(pin.lat, pin.lng)
                            title = pin.title
                            snippet = pin.snippet
                            setAnchor(Marker.ANCHOR_CENTER, if (pin.kind == PinKind.ME) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
                            pinIcon(map.context, pin, selected)?.let { icon = it }
                            setOnMarkerClickListener { m, _ ->
                                if (interactive) {
                                    if (pin.key != ME_PIN_KEY) select(pin.key)
                                    m.showInfoWindow()
                                }
                                true
                            }
                        },
                    )
                }
                map.invalidate()
            },
        )
        overlay()
        Text(
            "© OpenStreetMap contributors",
            fontSize = 10.sp,
            color = FarmColors.TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
