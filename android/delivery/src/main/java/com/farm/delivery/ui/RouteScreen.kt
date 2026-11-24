package com.farm.delivery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.farm.core.ui.IconLabel
import androidx.compose.ui.graphics.vector.ImageVector

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.LogisticsApi
import com.farm.core.api.Pool
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.core.auth.AuthManager
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.ErrorState
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.LoadingSkeleton
import com.farm.core.ui.MapPin
import com.farm.core.ui.Pill
import com.farm.core.ui.PinColors
import com.farm.core.ui.PinKind
import com.farm.core.ui.ScreenHeader
import com.farm.core.ui.TextVariant
import com.farm.core.ui.TileMap
import com.farm.core.ui.mePin
import com.farm.delivery.route.LocationSharing
import com.farm.delivery.route.RouteStop
import com.farm.delivery.route.RouteTracker
import com.farm.delivery.route.StopStatus
import com.farm.delivery.route.StopType
import com.farm.delivery.route.TrackingState
import com.farm.delivery.route.dialUri
import com.farm.delivery.route.localIsoDate
import com.farm.delivery.route.mapsUrl
import com.farm.delivery.route.markStopDone
import com.farm.delivery.route.open
import com.farm.delivery.route.routeLengthM
import com.farm.delivery.route.routeLine
import com.farm.delivery.route.routeStops
import com.farm.delivery.route.routeSummary
import com.farm.delivery.route.withStop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun formatDay(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("en", "IN")))
}.getOrDefault(iso)

private fun RouteStop.color(): Color = when {
    !status.open -> PinColors.Muted
    type == StopType.PICKUP -> PinColors.Pickup
    else -> PinColors.Drop
}

private fun RouteStop.glyph(): ImageVector = if (type == StopType.PICKUP) Icons.Rounded.Inventory2 else Icons.Rounded.Home

/**
 * Today's route: the signed-in partner's pool from GET /logistics/my-route, stops in driving order.
 * The next stop is shown in full; later stops are one-line rows that open on tap, and finished
 * stops fold away. "Navigate" opens Google Maps; pickup and delivery go through their sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(dateParam: String?, onBack: (() -> Unit)?, onHome: () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    val isPartner = auth.isAuthenticated && auth.hasRole("delivery")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = localIsoDate()
    val poolDate = if (dateParam != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateParam)) dateParam else today

    var pool by remember { mutableStateOf<Pool?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var deliveryStop by remember { mutableStateOf<RouteStop?>(null) }
    var pickupStop by remember { mutableStateOf<RouteStop?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var skipStop by remember { mutableStateOf<RouteStop?>(null) }
    var busyStopId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var selectedStopId by remember { mutableStateOf<String?>(null) }
    var showCompleted by remember { mutableStateOf(false) }

    suspend fun loadRoute() {
        load { LogisticsApi.getMyRoute(poolDate) }
            .onSuccess { pool = it; error = null }
            .onFailure { error = it }
        loaded = true
        refreshing = false
    }

    // A push (route offered, stop changed) or coming back to the app re-reads the route too.
    val tick = com.farm.core.ui.rememberRefreshTick()
    LaunchedEffect(isPartner, reloadKey, tick) { if (isPartner) loadRoute() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            toast = null
        }
    }

    val stops = routeStops(pool)
    val summary = routeSummary(stops)
    var available by remember { mutableStateOf(Availability.get(context)) }
    // Share the position while working a route, and also while waiting for one when the partner
    // is available, so dispatch can offer routes near them.
    LocationSharing(active = isPartner && (summary.next != null || available), poolId = pool?.id)
    val pings by RouteTracker.state.collectAsState()

    if (auth.isLoading) return CenteredLoading()
    if (!isPartner) {
        Column(
            Modifier.fillMaxSize().background(FarmColors.Background).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FText("Access denied. Please log in as a delivery partner.", textAlign = TextAlign.Center)
            FarmButton("Go Back", onHome, Modifier.padding(top = 16.dp), variant = ButtonVariant.Secondary)
        }
        return
    }

    val dayLabel = if (poolDate == today) "today" else formatDay(poolDate)
    // With no pool on the asked day, logistics answers with the partner's active pool from another day.
    val shownDate = pool?.poolDate?.ifEmpty { null } ?: poolDate

    fun onStopDone(stopId: String) {
        pool = markStopDone(pool, stopId)
        toast = "Stop completed."
        selectedStopId = null
        scope.launch { loadRoute() }
    }

    /** POST arrive / skip for one stop; the server's answer replaces the stop, a refusal is shown. */
    fun stopAction(stop: RouteStop, done: String, call: suspend () -> com.farm.core.api.Stop) {
        busyStopId = stop.id
        actionError = null
        scope.launch {
            load { call() }
                .onSuccess { pool = withStop(pool, it); toast = done; loadRoute() }
                .onFailure { actionError = loadErrorMessage(it) }
            busyStopId = null
        }
    }

    fun callCustomer(stop: RouteStop) {
        val uri = dialUri(stop.phone) ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No phone app on this device. Number: ${stop.phone}", Toast.LENGTH_LONG).show()
        }
    }

    fun navigate(stop: RouteStop) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl(stop.lat, stop.lng, stop.label))))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Could not open Google Maps on this device.", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    fun FullStop(stop: RouteStop, isNext: Boolean) = StopCard(
        stop = stop,
        isNext = isNext,
        busy = busyStopId == stop.id,
        onNavigate = ::navigate,
        onArrive = { stopAction(it, "Arrival recorded.") { LogisticsApi.arrive(it.id) } },
        onAction = { if (it.type == StopType.PICKUP) pickupStop = it else deliveryStop = it },
        onSkip = { skipStop = it },
        onCall = ::callCustomer,
        onCollapse = if (isNext) null else ({ selectedStopId = null }),
    )

    val openStops = stops.filter { it.status.open }
    val doneStops = stops.filter { !it.status.open }
    val later = openStops.filter { it.id != summary.next?.id }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader(if (shownDate == today) "Today's Route" else "Route · ${formatDay(shownDate)}", onBack)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reloadKey++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (loaded && summary.next == null) {
                    item {
                        AvailabilityCard(available) { on ->
                            available = on
                            Availability.set(context, on)
                        }
                    }
                }
                if (available) {
                    item {
                        RouteOffers(reloadKey, pings.lat, pings.lng) { accepted ->
                            pool = accepted
                            toast = "Route accepted. Your stops are below."
                            reloadKey++
                        }
                    }
                }
                when {
                    !loaded -> item { LoadingSkeleton() }
                    error != null && pool == null -> item {
                        ErrorState(error, { reloadKey++ }, title = "Could not load your route")
                    }
                    stops.isEmpty() -> item {
                        FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight) {
                            FText("No route assigned for $dayLabel.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
                            FText(
                                "Pull down to check again once dispatch has assigned you a pool.",
                                variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    else -> {
                        toast?.let { item { Banner(it, FarmColors.PrimaryLight, FarmColors.PrimaryDark, borderColor = FarmColors.Border, icon = Icons.Rounded.CheckCircle) } }
                        actionError?.let { item { Banner(it, FarmColors.ErrorBg, FarmColors.Error, icon = Icons.Rounded.ErrorOutline) } }
                        item {
                            RouteMap(stops, routeLine(pool, stops), summary.next, pings, selectedStopId ?: summary.next?.id) { selectedStopId = it }
                        }
                        item {
                            RouteOverview(
                                total = stops.size, done = summary.done, pickups = summary.pickups, drops = summary.drops,
                                lengthM = routeLengthM(stops), finished = summary.finished, tracking = pings,
                            )
                        }
                        summary.next?.let { next ->
                            item { SectionLabel("Next stop") }
                            item(key = next.id) { FullStop(next, isNext = true) }
                        }
                        if (later.isNotEmpty()) {
                            item { SectionLabel("Coming up (${later.size})") }
                            items(later, key = { it.id }) { stop ->
                                if (selectedStopId == stop.id) FullStop(stop, isNext = false)
                                else StopRow(stop) { selectedStopId = stop.id }
                            }
                        }
                        if (doneStops.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    "Completed (${doneStops.size})  ${if (showCompleted) "– hide" else "+ show"}",
                                    Modifier.fillMaxWidth().clickable { showCompleted = !showCompleted }.padding(vertical = 4.dp),
                                )
                            }
                            if (showCompleted) items(doneStops, key = { it.id }) { stop -> StopRow(stop, onClick = null) }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    deliveryStop?.let { stop ->
        ProofOfDeliverySheet(stop = stop, onClose = { deliveryStop = null }, onSuccess = ::onStopDone)
    }
    pickupStop?.let { stop ->
        PickupSheet(stop = stop, onClose = { pickupStop = null }, onSuccess = ::onStopDone)
    }
    skipStop?.let { stop ->
        SkipDialog(stop, onDismiss = { skipStop = null }) { reason ->
            skipStop = null
            stopAction(stop, "Stop skipped.") { LogisticsApi.skip(stop.id, reason) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextLabel,
        letterSpacing = 0.6.sp, modifier = modifier.padding(top = 4.dp),
    )
}

private val SKIP_REASONS = listOf("Customer not available", "Address not found", "Farm closed", "Vehicle problem")

@Composable
private fun SkipDialog(stop: RouteStop, onDismiss: () -> Unit, onSkip: (String) -> Unit) {
    var reason by remember { mutableStateOf(SKIP_REASONS[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip stop ${stop.seq}?") },
        text = {
            Column {
                FText("Dispatch is told the reason. A skipped stop cannot be completed later today.", variant = TextVariant.Caption)
                SKIP_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { reason = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSkip(reason) }) { Text("Skip stop", color = FarmColors.Error) } },
        dismissButton = { TextButton(onDismiss) { Text("Keep") } },
    )
}

/**
 * The stops on OpenStreetMap tiles in driving order along the road [line]: green parcel pickups, orange house drops (grey once
 * done), your position as a blue dot with a dashed line to the next stop.
 */
@Composable
private fun RouteMap(stops: List<RouteStop>, line: List<Pair<Double, Double>>, next: RouteStop?, tracking: TrackingState, selectedId: String?, onSelect: (String) -> Unit) {
    val first = stops.first()
    val me = if (tracking.lat != null && tracking.lng != null) mePin(tracking.lat, tracking.lng) else null
    TileMap(
        pins = stops.map {
            MapPin(
                it.id, it.lat, it.lng, "${it.seq}. ${it.label}", it.address,
                kind = if (it.type == StopType.PICKUP) PinKind.PICKUP else PinKind.DROP,
                badge = "${it.seq}",
                muted = !it.status.open,
            )
        } + listOfNotNull(me),
        centerLat = first.lat,
        centerLng = first.lng,
        selectedKey = selectedId,
        onSelect = onSelect,
        path = line,
        leadIn = if (me != null && next != null) listOf(me.lat to me.lng, next.lat to next.lng) else emptyList(),
        description = "Route map with ${stops.size} stops",
        modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp)),
        overlay = { MapLegend(Modifier.align(Alignment.TopStart).padding(8.dp), showMe = me != null) },
    )
}

/** Progress, counts, distance and location sharing in one compact card. */
@Composable
private fun RouteOverview(
    total: Int,
    done: Int,
    pickups: Int,
    drops: Int,
    lengthM: Double,
    finished: Boolean,
    tracking: TrackingState,
) {
    val pct = if (total > 0) done.toFloat() / total else 0f
    FarmCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (finished) {
                IconLabel(Icons.Rounded.TaskAlt, "Route complete", Modifier.weight(1f), color = FarmColors.Primary, fontSize = 16, fontWeight = FontWeight.Bold)
            } else {
                Text("$done of $total stops done", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark, modifier = Modifier.weight(1f))
            }
            Text("~${formatDistance(lengthM / 1000)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextLabel)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(FarmColors.Border)) {
            Box(Modifier.fillMaxWidth(pct).height(6.dp).background(FarmColors.Primary))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconLabel(Icons.Rounded.Inventory2, "$pickups pickups", color = PinColors.Pickup, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            IconLabel(Icons.Rounded.Home, "$drops drops", color = PinColors.Drop, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (tracking.isTracking) FarmColors.Primary else FarmColors.TextMuted))
            Text(
                if (tracking.isTracking) " Sharing location" else " Location off",
                fontSize = 12.sp, color = FarmColors.TextLabel,
            )
        }
        tracking.error?.let {
            IconLabel(Icons.Rounded.ErrorOutline, it, Modifier.padding(top = 6.dp), color = FarmColors.Error, fontSize = 12)
        }
    }
}

/** Numbered circle in the stop's colour, the same as its pin on the map. */
@Composable
private fun StopBadge(stop: RouteStop, size: Int = 30) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(stop.color()),
        contentAlignment = Alignment.Center,
    ) { Text("${stop.seq}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.2).sp) }
}

private fun statusText(s: StopStatus): String? = when (s) {
    StopStatus.DONE -> "Done"
    StopStatus.SKIPPED -> "Skipped"
    StopStatus.ARRIVED -> "At stop"
    StopStatus.PENDING -> null
}

/** One line per stop: number, parcel / house icon, name and address. Tapping opens the full card. */
@Composable
private fun StopRow(stop: RouteStop, onClick: (() -> Unit)?) {
    val isDone = !stop.status.open
    FarmCard(Modifier.fillMaxWidth().alpha(if (isDone) 0.65f else 1f), padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StopBadge(stop, size = 26)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(stop.glyph(), null, tint = stop.color(), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stop.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isDone) FarmColors.TextMuted else FarmColors.TextDark,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stop.address, fontSize = 12.sp, color = FarmColors.TextLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            statusText(stop.status)?.let { Text(it, fontSize = 12.sp, color = FarmColors.TextLabel, modifier = Modifier.padding(start = 8.dp)) }
        }
    }
}

@Composable
private fun StopCard(
    stop: RouteStop,
    isNext: Boolean,
    busy: Boolean,
    onNavigate: (RouteStop) -> Unit,
    onArrive: (RouteStop) -> Unit,
    onAction: (RouteStop) -> Unit,
    onSkip: (RouteStop) -> Unit,
    onCall: (RouteStop) -> Unit,
    onCollapse: (() -> Unit)?,
) {
    val isDone = !stop.status.open
    val color = stop.color()
    val border = if (isNext) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null

    FarmCard(Modifier.fillMaxWidth().alpha(if (isDone) 0.6f else 1f), border = border, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StopBadge(stop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                IconLabel(
                    stop.glyph(), if (stop.type == StopType.PICKUP) "PICKUP" else "DROP",
                    color = color, fontSize = 11, fontWeight = FontWeight.Bold,
                )
                Text(
                    stop.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isDone) FarmColors.TextMuted else FarmColors.TextDark,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                )
            }
            if (stop.status == StopStatus.ARRIVED) Pill("AT STOP", FarmColors.InfoBg, FarmColors.Info, fontSize = 10)
            if (onCollapse != null) {
                Icon(Icons.Rounded.ExpandLess, "Collapse", tint = FarmColors.TextLabel, modifier = Modifier.clickable(onClick = onCollapse).padding(6.dp))
            }
        }
        FText(stop.address, variant = TextVariant.Caption, color = FarmColors.TextLabel, modifier = Modifier.padding(top = 8.dp))
        if (stop.items.isNotEmpty()) {
            FText(stop.items.joinToString(" · "), variant = TextVariant.Caption, color = FarmColors.TextBody, modifier = Modifier.padding(top = 4.dp))
        }

        stop.notes?.let {
            IconLabel(
                Icons.Rounded.EditNote, it,
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(FarmColors.WarningBg).padding(horizontal = 10.dp, vertical = 8.dp),
                color = FarmColors.TextDark, iconColor = FarmColors.Warning,
            )
        }

        if (!isDone) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Navigate", { onNavigate(stop) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.Navigation)
                if (stop.status == StopStatus.PENDING) {
                    FarmButton(if (busy) "Saving…" else "I've arrived", { onArrive(stop) }, Modifier.weight(1f), loading = busy, enabled = !busy)
                } else {
                    FarmButton(
                        if (stop.type == StopType.PICKUP) "Confirm pickup" else "Deliver",
                        { onAction(stop) }, Modifier.weight(1f), enabled = !busy,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (stop.phone != null) {
                    TextButton({ onCall(stop) }) {
                        IconLabel(Icons.Rounded.Call, "Call ${if (stop.type == StopType.PICKUP) "farm" else "customer"}", color = FarmColors.Primary)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton({ onSkip(stop) }, enabled = !busy) { Text("Skip stop", color = FarmColors.TextLabel, fontSize = 13.sp) }
            }
        }
    }
}
