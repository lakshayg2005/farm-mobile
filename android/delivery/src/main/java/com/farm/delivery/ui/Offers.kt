package com.farm.delivery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.farm.core.ui.IconLabel
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.ApiException
import com.farm.core.api.LogisticsApi
import com.farm.core.api.Pool
import com.farm.core.api.RouteOffer
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.MapPin
import com.farm.core.ui.PinColors
import com.farm.core.ui.PinKind
import com.farm.core.ui.TextVariant
import com.farm.core.ui.TileMap
import com.farm.core.ui.mePin
import com.farm.delivery.route.haversine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** How often open offers are re-read while the route screen is shown (pushes also refresh it). */
const val OFFER_POLL_MS = 10_000L

/** "2 pickups · 2 drops". */
fun offerCounts(o: RouteOffer): String =
    "${o.pickupCount} ${if (o.pickupCount == 1) "pickup" else "pickups"} · " +
        "${o.dropCount} ${if (o.dropCount == 1) "drop" else "drops"}"

/** "1.2 km", or "850 m" under a kilometre. */
fun formatDistance(km: Double): String =
    if (km < 1) "${(km * 1000).toInt()} m" else String.format(Locale.US, "%.1f km", km)

/** How far the partner rides for an offer: to the first pickup, the route itself, and both together. */
data class OfferTrip(val toStartKm: Double?, val routeKm: Double?) {
    val totalKm: Double? get() = if (toStartKm == null && routeKm == null) null else (toStartKm ?: 0.0) + (routeKm ?: 0.0)
}

/**
 * The trip for [o]. The server's figures win; without them the route is measured straight-line
 * through its stops and the way in from [meLat]/[meLng] to the first stop.
 */
fun offerTrip(o: RouteOffer, meLat: Double? = null, meLng: Double? = null): OfferTrip {
    val stops = o.stops.sortedBy { it.seq }
    val routeKm = o.totalKm ?: stops.takeIf { it.size >= 2 }
        ?.zipWithNext()?.sumOf { (a, b) -> haversine(a.lat, a.lng, b.lat, b.lng) / 1000 }
    val start = stops.firstOrNull()?.let { it.lat to it.lng } ?: o.firstPickup?.let { it.lat to it.lng }
    val toStart = o.distanceKm ?: if (meLat != null && meLng != null && start != null) {
        haversine(meLat, meLng, start.first, start.second) / 1000
    } else null
    return OfferTrip(toStart, routeKm)
}

/** True when the accept lost the race (another partner was first). */
fun isTaken(e: Throwable?): Boolean = (e as? ApiException)?.let { it.code == "POOL_TAKEN" || it.status == 409 } == true

/** Whether the partner wants new route offers (kept on this phone). */
object Availability {
    private const val PREFS = "delivery_prefs"
    private const val KEY = "available_for_routes"
    fun get(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun set(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
}

/**
 * "Available for new routes": while on, the phone shares its position (so dispatch can offer
 * nearby routes) even before a route is assigned.
 */
@Composable
fun AvailabilityCard(available: Boolean, onChange: (Boolean) -> Unit) {
    FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Available for new routes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                FText(
                    if (available) "Sharing your position so nearby routes find you" else "You won't get route offers",
                    variant = TextVariant.Caption,
                )
            }
            Switch(
                checked = available,
                onCheckedChange = onChange,
                modifier = Modifier.semantics { contentDescription = "Available for new routes" },
                colors = SwitchDefaults.colors(checkedTrackColor = FarmColors.Primary),
            )
        }
    }
}

/**
 * Open route offers, re-read every [OFFER_POLL_MS] and whenever [refreshKey] changes. Each offer
 * shows its whole route on a small map (with the partner's own position from [meLat]/[meLng]) and
 * how far the trip is in total. Accept takes the route (first partner wins); if someone was
 * faster the card disappears with a note.
 */
@Composable
fun RouteOffers(refreshKey: Int, meLat: Double?, meLng: Double?, onAccepted: (Pool) -> Unit) {
    val scope = rememberCoroutineScope()
    val offers = remember { mutableStateListOf<RouteOffer>() }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val accepted by rememberUpdatedState(onAccepted)

    suspend fun reload() {
        load { LogisticsApi.myOffers() }
            .onSuccess { offers.clear(); offers.addAll(it); error = null }
            .onFailure { error = loadErrorMessage(it) }
    }
    LaunchedEffect(refreshKey) {
        while (true) {
            reload()
            delay(OFFER_POLL_MS)
        }
    }

    if (offers.isEmpty() && note == null && error == null) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        note?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.Warning) }
        error?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.Error) }
        offers.forEach { o ->
            key(o.poolId) {
                OfferCard(
                    o, meLat, meLng, busy,
                    onDecline = {
                        busy = o.poolId
                        scope.launch {
                            load { LogisticsApi.declineOffer(o.poolId) }
                            offers.remove(o)
                            busy = null
                        }
                    },
                    onAccept = {
                        busy = o.poolId
                        note = null
                        scope.launch {
                            load { LogisticsApi.acceptOffer(o.poolId) }
                                .onSuccess { pool -> offers.clear(); accepted(pool) }
                                .onFailure { e ->
                                    if (isTaken(e)) {
                                        offers.remove(o)
                                        note = "Another delivery partner accepted that route first."
                                    } else {
                                        note = loadErrorMessage(e)
                                    }
                                }
                            busy = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OfferCard(
    o: RouteOffer,
    meLat: Double?,
    meLng: Double?,
    busy: String?,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
) {
    val trip = offerTrip(o, meLat, meLng)
    FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New route offer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark, modifier = Modifier.weight(1f))
            FText("${o.stopCount} stops · ${o.poolDate.ifBlank { "Today" }}", variant = TextVariant.Caption)
        }
        OfferMap(o, meLat, meLng, Modifier.padding(top = 8.dp))
        TripStats(trip, Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconLabel(Icons.Rounded.Inventory2, "${o.pickupCount} ${if (o.pickupCount == 1) "pickup" else "pickups"}", color = PinColors.Pickup, fontSize = 12, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            IconLabel(Icons.Rounded.Home, "${o.dropCount} ${if (o.dropCount == 1) "drop" else "drops"}", color = PinColors.Drop, fontSize = 12, fontWeight = FontWeight.SemiBold)
        }
        o.firstPickup?.address?.takeIf { it.isNotBlank() }?.let {
            IconLabel(Icons.Rounded.Agriculture, "Starts at $it", Modifier.padding(top = 4.dp), fontSize = 12)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FarmButton("Decline", onDecline, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = busy == null)
            FarmButton(
                if (busy == o.poolId) "Accepting…" else "Accept route", onAccept,
                Modifier.weight(1f), loading = busy == o.poolId, enabled = busy == null,
            )
        }
    }
}

/** The whole offered route: you (blue dot), a dashed line to the first stop, then every stop in order. */
@Composable
private fun OfferMap(o: RouteOffer, meLat: Double?, meLng: Double?, modifier: Modifier) {
    val stops = o.stops.sortedBy { it.seq }
    val stopPins = if (stops.isNotEmpty()) {
        stops.mapIndexed { i, s ->
            val pickup = s.stopType == "PICKUP"
            MapPin(
                "s$i", s.lat, s.lng, "${i + 1}. ${if (pickup) "Pickup" else "Drop"}",
                kind = if (pickup) PinKind.PICKUP else PinKind.DROP, badge = "${i + 1}",
            )
        }
    } else {
        listOfNotNull(o.firstPickup?.let { MapPin("s0", it.lat, it.lng, "First pickup", it.address, kind = PinKind.PICKUP) })
    }
    if (stopPins.isEmpty()) return
    val me = if (meLat != null && meLng != null) mePin(meLat, meLng) else null
    TileMap(
        pins = stopPins + listOfNotNull(me),
        centerLat = stopPins[0].lat,
        centerLng = stopPins[0].lng,
        path = stopPins.map { it.lat to it.lng },
        leadIn = if (me != null) listOf(me.lat to me.lng, stopPins[0].lat to stopPins[0].lng) else emptyList(),
        interactive = false,
        description = "Map of the offered route with ${stopPins.size} stops",
        modifier = modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp)),
        overlay = { MapLegend(Modifier.align(Alignment.TopStart).padding(6.dp), showMe = me != null) },
    )
}

/** "To first pickup · Route · Total trip" side by side, total emphasised. */
@Composable
private fun TripStats(trip: OfferTrip, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FarmColors.Background).padding(vertical = 8.dp),
    ) {
        TripStat(trip.toStartKm, "to 1st pickup", Modifier.weight(1f))
        TripStat(trip.routeKm, "route", Modifier.weight(1f))
        TripStat(trip.totalKm, "total trip", Modifier.weight(1f), FarmColors.Primary)
    }
}

@Composable
private fun TripStat(km: Double?, label: String, modifier: Modifier, color: Color = FarmColors.TextDark) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(km?.let(::formatDistance) ?: "—", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
        FText(label, variant = TextVariant.Caption)
    }
}

/** A small key for the delivery maps: blue dot = you, green box = pickup, orange house = drop. */
@Composable
fun MapLegend(modifier: Modifier = Modifier, showMe: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xE6FFFFFF)).padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMe) LegendItem(Icons.Rounded.MyLocation, "You", PinColors.Me)
        LegendItem(Icons.Rounded.Inventory2, "Pickup", PinColors.Pickup)
        LegendItem(Icons.Rounded.Home, "Drop", PinColors.Drop)
    }
}

@Composable
private fun LegendItem(icon: ImageVector, label: String, color: Color) {
    IconLabel(icon, label, color = color, fontSize = 11, fontWeight = FontWeight.SemiBold)
}
