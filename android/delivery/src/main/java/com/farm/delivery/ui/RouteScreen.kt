package com.farm.delivery.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.Pool
import com.farm.core.auth.AuthManager
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.Pill
import com.farm.core.ui.ScreenHeader
import com.farm.core.ui.TextVariant
import com.farm.delivery.route.RouteStop
import com.farm.delivery.route.StopStatus
import com.farm.delivery.route.StopType
import com.farm.delivery.route.localIsoDate
import com.farm.delivery.route.mapsUrl
import com.farm.delivery.route.markStopDone
import com.farm.delivery.route.rememberLocationPings
import com.farm.delivery.route.routeStops
import com.farm.delivery.route.routeSummary
import com.farm.core.api.LogisticsApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun formatDay(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("en", "IN")))
}.getOrDefault(iso)

/**
 * Today's route: the signed-in partner's pool from GET /logistics/my-route, stops in driving order.
 * "Navigate" opens Google Maps; pickup and delivery go through their sheets to the logistics API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(dateParam: String?, onBack: () -> Unit, onHome: () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    val isPartner = auth.isAuthenticated && auth.hasRole("delivery")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = localIsoDate()
    val poolDate = if (dateParam != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateParam)) dateParam else today

    var pool by remember { mutableStateOf<Pool?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var deliveryStop by remember { mutableStateOf<RouteStop?>(null) }
    var pickupStop by remember { mutableStateOf<RouteStop?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            pool = LogisticsApi.getMyRoute(poolDate)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Try again in a moment."
        } finally {
            loaded = true
            refreshing = false
        }
    }

    LaunchedEffect(isPartner, reloadKey) { if (isPartner) load() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            toast = null
        }
    }

    val stops = routeStops(pool)
    val summary = routeSummary(stops)
    val pings = rememberLocationPings(isPartner && summary.next != null)

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
        toast = "Stop status updated successfully!"
        scope.launch { load() }
    }

    fun navigate(stop: RouteStop) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl(stop.lat, stop.lng, stop.label))))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open Google Maps on this device.", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader(if (shownDate == today) "Today's Route" else "Route · ${formatDay(shownDate)}", onBack)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reloadKey++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    !loaded -> item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FarmColors.Primary)
                        }
                    }
                    error != null && pool == null -> item {
                        FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight) {
                            FText("Could not load your route", variant = TextVariant.H2)
                            FText(error ?: "", variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp))
                            FarmButton("Try again", { reloadKey++ }, Modifier.padding(top = 12.dp))
                        }
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
                        item { MapBanner(stops.size, summary.done) }
                        item { TrackingCard(pings.isTracking, pings.pingCount, pings.lastPingTime, pings.error) }
                        toast?.let { item { Banner("🎉 $it", Color(0xFFE8F5E9), Color(0xFF1B5E20), borderColor = Color(0xFFA5D6A7)) } }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryChip(summary.pickups, "Pickups", Modifier.weight(1f))
                                SummaryChip(summary.drops, "Drops", Modifier.weight(1f))
                                SummaryChip(summary.done, "Done", Modifier.weight(1f), FarmColors.Primary)
                            }
                        }
                        if (summary.finished) item {
                            FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight) {
                                FText("🎉 Route complete! Great work today.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
                            }
                        }
                        items(stops, key = { it.id }) { stop ->
                            StopCard(
                                stop = stop,
                                isNext = summary.next?.id == stop.id,
                                onNavigate = ::navigate,
                                onAction = { if (it.type == StopType.PICKUP) pickupStop = it else deliveryStop = it },
                            )
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
}

@Composable
private fun SummaryChip(value: Int, label: String, modifier: Modifier, color: Color = FarmColors.TextDark) {
    FarmCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            FText(label, variant = TextVariant.Caption)
        }
    }
}

@Composable
private fun MapBanner(total: Int, done: Int) {
    val pct = if (total > 0) Math.round(done * 100f / total) else 0
    Column {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
            Column(
                Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF1B5E20)),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🗺️", fontSize = 44.sp)
                FText("$total stops on this route", variant = TextVariant.Caption, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFC8E6C9))) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).background(FarmColors.Primary))
            }
        }
        FText(
            "$done / $total stops completed ($pct%)", variant = TextVariant.Caption, color = FarmColors.TextLabel,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun TrackingCard(isTracking: Boolean, pingCount: Int, lastPing: String?, error: String?) {
    FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp), border = BorderStroke(1.dp, FarmColors.Border)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isTracking) Color(0xFF2E7D32) else Color(0xFF9E9E9E)))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isTracking) "GPS Tracking Active" else "GPS Tracking Paused",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark, modifier = Modifier.weight(1f),
            )
            Pill("30s interval", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        }
        FText(
            if (isTracking) "Reporting coordinates to logistics dispatch · $pingCount pings sent${lastPing?.let { " (last: $it)" } ?: ""}"
            else "Tracking resumes automatically when pending stops exist.",
            variant = TextVariant.Caption, color = Color(0xFF666666), modifier = Modifier.padding(top = 4.dp),
        )
        if (error != null) {
            FText("⚠️ $error", variant = TextVariant.Caption, color = Color(0xFFC62828), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StopCard(stop: RouteStop, isNext: Boolean, onNavigate: (RouteStop) -> Unit, onAction: (RouteStop) -> Unit) {
    val isDone = stop.status == StopStatus.DONE
    val color = if (stop.type == StopType.PICKUP) Color(0xFF1B5E20) else Color(0xFF1565C0)
    val bg = if (stop.type == StopType.PICKUP) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)

    FarmCard(Modifier.fillMaxWidth().alpha(if (isDone) 0.6f else 1f)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 2.dp).size(30.dp).clip(CircleShape).background(if (isDone) Color(0xFFCCCCCC) else color),
                contentAlignment = Alignment.Center,
            ) { Text("${stop.seq}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (stop.type == StopType.PICKUP) "📦 PICKUP" else "🚪 DROP", bg, color, fontSize = 10, border = color)
                    if (isNext) Pill("NEXT", Color(0xFFFF6F00), Color.White, fontSize = 10)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (stop.status) { StopStatus.DONE -> "✅"; StopStatus.SKIPPED -> "⏭️"; else -> "⏳" },
                        fontSize = 16.sp,
                    )
                }
                Text(
                    stop.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isDone) Color(0xFF888888) else FarmColors.TextDark,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        FText("📍 ${stop.address}", variant = TextVariant.Caption, color = FarmColors.TextLabel, modifier = Modifier.padding(vertical = 8.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFAFAFA))
                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(10.dp),
        ) {
            FText("Items", variant = TextVariant.Label)
            stop.items.forEach { FText("• $it", variant = TextVariant.Caption, color = Color(0xFF444444), modifier = Modifier.padding(top = 2.dp)) }
        }

        if (stop.type == StopType.DROP && stop.otp != null && !isDone) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF3E5F5))
                    .border(1.dp, Color(0xFFCE93D8), RoundedCornerShape(8.dp)).padding(10.dp),
            ) {
                FText("Customer OTP (Demo Code)", variant = TextVariant.Label, color = Color(0xFF7B1FA2))
                Text(stop.otp, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A), letterSpacing = 6.sp, modifier = Modifier.padding(top = 4.dp))
                FText("In production, customer shares this upon delivery", variant = TextVariant.Caption, color = Color(0xFF7B1FA2), modifier = Modifier.padding(top = 2.dp))
            }
        }

        if (!isDone) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("🗺️  Navigate", { onNavigate(stop) }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                FarmButton(
                    if (stop.type == StopType.PICKUP) "📦 Verify Pickup" else "📸 Deliver",
                    { onAction(stop) }, Modifier.weight(1f),
                )
            }
        }
    }
}
