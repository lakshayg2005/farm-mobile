package com.farm.customer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.AppNotification
import com.farm.core.api.HarvestOrderChoice
import com.farm.core.api.NetworkException
import com.farm.core.api.NotificationApi
import com.farm.core.api.OrderApi
import com.farm.core.api.OrderDetail
import com.farm.core.api.OrderRating
import com.farm.core.api.OrderRatingCreate
import com.farm.core.api.OrderSummary
import com.farm.core.api.LogisticsApi
import com.farm.core.api.Track
import com.farm.core.auth.AuthManager
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.FarmInput
import com.farm.core.ui.Pill
import com.farm.core.ui.ScreenHeader
import com.farm.core.ui.TextVariant
import com.farm.core.ui.hex
import com.farm.customer.checkout.AddressForm
import com.farm.customer.checkout.AddressResult
import com.farm.customer.checkout.formatRupees
import com.farm.customer.checkout.validateAddress
import com.farm.customer.orders.HarvestStage
import com.farm.customer.orders.LatLng
import com.farm.customer.orders.Phase
import com.farm.customer.orders.TIMELINE_STEPS
import com.farm.customer.orders.addressLabel
import com.farm.customer.orders.canRate
import com.farm.customer.orders.deliveryDates
import com.farm.customer.orders.distanceLabel
import com.farm.customer.orders.etaLabel
import com.farm.customer.orders.formatDate
import com.farm.customer.orders.formatDateTime
import com.farm.customer.orders.harvestStageOf
import com.farm.customer.orders.haversineKm
import com.farm.customer.orders.itemsLabel
import com.farm.customer.orders.notificationOrderId
import com.farm.customer.orders.orderTitle
import com.farm.customer.orders.parseChoiceError
import com.farm.customer.orders.phaseOf
import com.farm.customer.orders.project
import com.farm.customer.orders.rupees
import com.farm.customer.orders.seenLabel
import com.farm.customer.orders.shortId
import com.farm.customer.orders.shouldPoll
import com.farm.customer.orders.shouldPollHarvest
import com.farm.customer.orders.splitOrders
import com.farm.customer.orders.statusMeta
import com.farm.customer.orders.stepIndex
import com.farm.customer.orders.stepTimes
import com.farm.customer.orders.summarise
import com.farm.customer.orders.timeAgo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

@Composable
private fun RequireCustomer(message: String, onHome: () -> Unit, content: @Composable () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    when {
        auth.isLoading -> CenteredLoading()
        !auth.isAuthenticated || !auth.hasRole("customer") -> Column(
            Modifier.fillMaxSize().background(FarmColors.Background).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FText(message, textAlign = TextAlign.Center)
            FarmButton("Go Home", onHome, Modifier.padding(top = 16.dp), variant = ButtonVariant.Secondary)
        }
        else -> content()
    }
}

@Composable
private fun StatusBadge(status: String) {
    val m = statusMeta(status)
    Pill("${m.emoji} ${m.label}", hex(m.bg), hex(m.color), fontSize = 12)
}

// ─── Orders list ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onBack: () -> Unit, onHome: () -> Unit, onOrder: (String) -> Unit, onNotifications: () -> Unit) =
    RequireCustomer("Please log in as a customer to view orders.", onHome) {
        val scope = rememberCoroutineScope()
        var orders by remember { mutableStateOf<List<OrderSummary>>(emptyList()) }
        var cursor by remember { mutableStateOf<String?>(null) }
        var loaded by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var refreshing by remember { mutableStateOf(false) }
        var loadingMore by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }

        LaunchedEffect(reload) {
            try {
                val page = OrderApi.listOrders(null)
                orders = page.items
                cursor = page.nextCursor
                failed = false
            } catch (_: Exception) {
                failed = true
            }
            loaded = true
            refreshing = false
        }

        Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
            ScreenHeader("My Orders", onBack) {
                Text("🔔", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onNotifications).padding(6.dp))
            }
            PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                val (active, past) = splitOrders(orders)
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!loaded) item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FarmColors.Primary) } }
                    if (loaded && failed) item {
                        FarmCard(Modifier.fillMaxWidth()) {
                            FText("Could not load your orders", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            FText("Check your connection and try again.", variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            FarmButton("Try again", { reload++ }, Modifier.fillMaxWidth().padding(top = 16.dp))
                        }
                    }
                    if (active.isNotEmpty()) {
                        item { FText("Active Orders", variant = TextVariant.Label) }
                        items(active, key = { it.id }) { OrderCard(it) { onOrder(it.id) } }
                    }
                    if (past.isNotEmpty()) {
                        item { FText("Past Orders", variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp)) }
                        items(past, key = { it.id }) { OrderCard(it) { onOrder(it.id) } }
                    }
                    if (loaded && !failed && orders.isEmpty()) item {
                        FarmCard(Modifier.fillMaxWidth()) {
                            Text("🛒", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            FText("No orders yet", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            FText("Browse produce and place your first order!", variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (cursor != null) item {
                        FarmButton(if (loadingMore) "Loading…" else "Load more orders", {
                            loadingMore = true
                            scope.launch {
                                try {
                                    val page = OrderApi.listOrders(cursor)
                                    orders = orders + page.items
                                    cursor = page.nextCursor
                                } catch (_: Exception) {
                                }
                                loadingMore = false
                            }
                        }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !loadingMore)
                    }
                }
            }
        }
    }

@Composable
private fun OrderCard(order: OrderSummary, onClick: () -> Unit) {
    FarmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FText(orderTitle(order.orderType, order.harvestOption), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                FText("${shortId(order.id)} · Placed ${formatDate(order.createdAt)}", variant = TextVariant.Caption)
            }
            Text(rupees(order.total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
        }
        FText(itemsLabel(order.itemCount), variant = TextVariant.Caption, modifier = Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(order.status)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 22.sp, color = Color(0xFFBBBBBB))
        }
    }
}

// ─── Order detail ───────────────────────────────────────────────────────────

/** Loads one order; [poll] returns the delay before the next refresh, or null to stop. */
@Composable
private fun rememberOrder(orderId: String, refreshKey: Int = 0, poll: ((OrderDetail) -> Long?)? = null): Triple<OrderDetail?, String?, Boolean> {
    var order by remember { mutableStateOf<OrderDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(orderId, refreshKey) {
        while (true) {
            try {
                order = OrderApi.getOrder(orderId)
                error = null
            } catch (e: Exception) {
                error = e.message
            }
            loaded = true
            val next = order?.let { poll?.invoke(it) } ?: break
            delay(next)
        }
    }
    return Triple(order, error, loaded)
}

@Composable
fun OrderDetailScreen(orderId: String, onBack: () -> Unit, onHome: () -> Unit, onTrack: (String) -> Unit) =
    RequireCustomer("Access denied.", onHome) {
        var reload by remember { mutableIntStateOf(0) }
        val (order, error, loaded) = rememberOrder(orderId, reload)
        if (!loaded) return@RequireCustomer CenteredLoading()
        Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
            ScreenHeader("Order Detail", onBack)
            if (order == null) {
                val notFound = error == "Order not found"
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    FText(if (notFound) "Order not found" else "Could not load the order", variant = TextVariant.H2)
                    if (!notFound) FarmButton("Try again", { reload++ }, Modifier.padding(top = 16.dp))
                    FarmButton("Back to Orders", onBack, Modifier.padding(top = 12.dp), variant = ButtonVariant.Secondary)
                }
                return@Column
            }
            val current = stepIndex(order.status)
            val times = stepTimes(order.events)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FarmCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            FText("${orderTitle(order.orderType, order.harvestOption)} ${shortId(order.id)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                            FText("Placed ${formatDateTime(order.createdAt)}", variant = TextVariant.Caption)
                            order.deliveryDate?.let { FText("Delivery by ${formatDate(it)}", variant = TextVariant.Caption) }
                        }
                        Text(rupees(order.total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                    }
                    Column(Modifier.padding(top = 10.dp)) {
                        order.items.forEach { LineRow("${it.name}  ×${qtyText(it.qty)} ${it.unit}", rupees(it.lineTotal)) }
                        if (order.deliveryFee > 0) LineRow("Delivery", rupees(order.deliveryFee))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFFEEEEEE))
                        Row { FText("Total", variant = TextVariant.Label, modifier = Modifier.weight(1f)); FText(rupees(order.total), variant = TextVariant.Label, color = FarmColors.Primary) }
                    }
                    addressLabel(order.deliveryAddress)?.let {
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFAFAFA)).padding(10.dp)) {
                            FText("Delivery Address", variant = TextVariant.Label)
                            FText("📍 $it", variant = TextVariant.Caption, color = FarmColors.TextLabel)
                        }
                    }
                }
                FText("Order Timeline", variant = TextVariant.Label)
                FarmCard(Modifier.fillMaxWidth()) {
                    if (order.status == "CANCELLED") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❌", fontSize = 32.sp)
                            FText("Order Cancelled", variant = TextVariant.H2, color = Color(0xFFC62828))
                            FText("This order was cancelled. Any payment is refunded to you.", variant = TextVariant.Caption, textAlign = TextAlign.Center)
                        }
                    } else {
                        TIMELINE_STEPS.forEachIndexed { idx, step -> TimelineStep(step, current, idx, idx == TIMELINE_STEPS.size - 1, times[idx]) }
                    }
                }
                FarmButton("Track delivery and rate", { onTrack(order.id) }, Modifier.fillMaxWidth())
            }
        }
    }

@Composable
private fun LineRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        FText(label, variant = TextVariant.Caption, color = Color(0xFF444444), modifier = Modifier.weight(1f))
        FText(value, variant = TextVariant.Caption, color = Color(0xFF444444))
    }
}

@Composable
private fun TimelineStep(status: String, current: Int, index: Int, isLast: Boolean, at: String?) {
    val meta = statusMeta(status)
    val done = index < current
    val isCurrent = index == current
    val future = index > current
    val dot = when { done -> FarmColors.Primary; isCurrent -> hex(meta.color); else -> Color(0xFFCCCCCC) }
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(dot), contentAlignment = Alignment.Center) {
                when {
                    done -> Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    isCurrent -> Text(meta.emoji, fontSize = 13.sp)
                    else -> Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
            }
            if (!isLast) Box(Modifier.width(2.dp).height(30.dp).background(if (done) FarmColors.Primary else Color(0xFFE0E0E0)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.alpha(if (future) 0.6f else 1f).padding(top = 4.dp)) {
            Text(
                meta.label, fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = when { isCurrent -> hex(meta.color); done -> FarmColors.Primary; else -> Color(0xFFBBBBBB) },
            )
            if (!future && at != null) FText(formatDateTime(at), variant = TextVariant.Caption, color = if (isCurrent) hex(meta.color) else FarmColors.TextMuted)
            if (isCurrent && at == null) FText("Current status", variant = TextVariant.Caption, color = hex(meta.color))
        }
    }
}

// ─── Notifications ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onHome: () -> Unit, onOrder: (String) -> Unit) =
    RequireCustomer("Please log in as a customer.", onHome) {
        val scope = rememberCoroutineScope()
        var items by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
        var unread by remember { mutableIntStateOf(0) }
        var cursor by remember { mutableStateOf<String?>(null) }
        var loaded by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var refreshing by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }

        LaunchedEffect(reload) {
            try {
                val page = NotificationApi.list(null)
                items = page.items; unread = page.unreadCount; cursor = page.nextCursor; failed = false
            } catch (_: Exception) {
                failed = true
            }
            loaded = true; refreshing = false
        }

        /** Marks read on screen first, puts it back if the server refuses. */
        fun markRead(id: String?) {
            val before = items to unread
            val now = Instant.now().toString()
            items = items.map { if (it.readAt == null && (id == null || it.id == id)) it.copy(readAt = now) else it }
            unread = if (id == null) 0 else maxOf(0, unread - 1)
            scope.launch {
                try {
                    if (id == null) NotificationApi.markAllRead() else NotificationApi.markRead(id)
                } catch (_: Exception) {
                    items = before.first; unread = before.second
                }
            }
        }

        Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
            ScreenHeader(if (unread > 0) "Notifications ($unread)" else "Notifications", onBack) {
                if (unread > 0) Text("Mark all read", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { markRead(null) }.padding(4.dp))
            }
            PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        !loaded -> item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FarmColors.Primary) } }
                        failed -> item {
                            FarmCard(Modifier.fillMaxWidth()) {
                                FText("Could not load notifications", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                FarmButton("Try again", { reload++ }, Modifier.fillMaxWidth().padding(top = 16.dp))
                            }
                        }
                        items.isEmpty() -> item {
                            FarmCard(Modifier.fillMaxWidth()) {
                                Text("🔔", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                                FText("All caught up!", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                FText("No notifications yet. We'll let you know when something happens.", variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        else -> items(items, key = { it.id }) { n ->
                            val isUnread = n.readAt == null
                            val orderId = notificationOrderId(n.payload)
                            FarmCard(
                                Modifier.fillMaxWidth(),
                                background = if (isUnread) Color(0xFFF1F8F4) else Color.White,
                                border = if (isUnread) BorderStroke(1.dp, Color(0xFFC8E6C9)) else null,
                                onClick = { if (isUnread) markRead(n.id); orderId?.let(onOrder) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isUnread) { Box(Modifier.size(8.dp).clip(CircleShape).background(FarmColors.Primary)); Spacer(Modifier.width(10.dp)) }
                                    Column(Modifier.weight(1f)) {
                                        FText(n.title, variant = TextVariant.Label, color = if (isUnread) FarmColors.TextDark else FarmColors.TextLabel)
                                        FText(n.body, variant = TextVariant.Caption, color = Color(0xFF555555))
                                        FText(timeAgo(n.createdAt), variant = TextVariant.Caption, fontSize = 11.sp)
                                    }
                                    if (orderId != null) Text("›", fontSize = 22.sp, color = Color(0xFFBBBBBB))
                                }
                            }
                        }
                    }
                    if (cursor != null) item {
                        FarmButton("Load older notifications", {
                            scope.launch {
                                runCatching { NotificationApi.list(cursor) }.onSuccess { items = items + it.items; cursor = it.nextCursor }
                            }
                        }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
                    }
                }
            }
        }
    }

// ─── Tracking & rating ──────────────────────────────────────────────────────

@Composable
fun TrackingScreen(orderId: String, onBack: () -> Unit, onOrders: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val (order, _, loaded) = rememberOrder(orderId, refresh) { if (it.status == "COMPLETED" || it.status == "CANCELLED") null else 15_000L }
    var track by remember { mutableStateOf<Track?>(null) }
    val latestOrder by androidx.compose.runtime.rememberUpdatedState(order)
    LaunchedEffect(orderId) {
        while (true) {
            track = runCatching { LogisticsApi.track(orderId) }.getOrElse { track }
            val o = latestOrder
            if (o != null && !shouldPoll(phaseOf(o.status, track))) break
            delay(10_000)
        }
    }

    if (!loaded) return CenteredLoading()
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Track delivery", onBack)
        if (order == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FText("Order not found", variant = TextVariant.H2)
                FarmButton("Back", onBack, Modifier.padding(top = 12.dp), variant = ButtonVariant.Secondary)
            }
            return@Column
        }
        val phase = phaseOf(order.status, track)
        val now = Instant.now()
        val drop = order.deliveryAddress?.location?.let { LatLng(it.lat, it.lng) }
        val pickup = order.items.firstOrNull()?.pickupLocation?.takeIf { it.coordinates.size >= 2 }?.let { LatLng(it.lat, it.lng) }
        val t = track
        val pLat = t?.partnerLat
        val pLng = t?.partnerLng
        val partner = if (pLat != null && pLng != null) LatLng(pLat, pLng) else null
        val live = phase == Phase.IN_TRANSIT || phase == Phase.POOLED
        val eta = etaLabel(t?.eta, now)
        val seen = seenLabel(t?.partnerUpdatedAt, now)

        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText(phase.text, variant = TextVariant.H2, fontSize = 18.sp)
            if (live) {
                FarmCard(Modifier.fillMaxWidth()) {
                    TrackingMap(partner, drop, if (phase == Phase.POOLED) pickup else null)
                    if (partner != null && drop != null) {
                        FText(
                            "${distanceLabel(haversineKm(partner, drop))} from you" + (if (eta.isNotEmpty()) " · arriving $eta" else "") +
                                (if (seen.isNotEmpty()) " · position updated $seen" else ""),
                            variant = TextVariant.Caption, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (partner == null) FText("Waiting for the partner's first position.", variant = TextVariant.Caption, modifier = Modifier.padding(top = 8.dp))
                    t?.partnerName?.let { FText(it + (t.partnerPhone?.let { p -> " · $p" } ?: ""), variant = TextVariant.Label, modifier = Modifier.padding(top = 6.dp)) }
                    t?.partnerPhone?.let { phone ->
                        FarmButton("Call the partner", {
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                        }, Modifier.fillMaxWidth().padding(top = 8.dp), variant = ButtonVariant.Secondary)
                    }
                }
            }
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Order ${order.id.take(8).uppercase()}", variant = TextVariant.Label)
                order.events.forEach { FText("${it.status.replace('_', ' ').lowercase()} · ${formatDateTime(it.occurredAt)}", variant = TextVariant.Caption) }
            }
            order.rating?.let { r ->
                FarmCard(Modifier.fillMaxWidth()) {
                    FText("Your rating: ${"★".repeat(r.score)}", variant = TextVariant.Label)
                    r.comment?.let { FText(it, variant = TextVariant.Caption) }
                }
            }
            if (canRate(order.status, order.rating != null)) RatingForm(order.id) { refresh++ }
            FarmButton("Back to orders", onOrders, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
    }
}

/** A plain canvas with pickup / drop / partner glyphs placed by their real coordinates. */
@Composable
private fun TrackingMap(partner: LatLng?, drop: LatLng?, pickup: LatLng?) {
    val points = listOfNotNull(pickup?.let { "pickup" to it }, drop?.let { "drop" to it }, partner?.let { "partner" to it })
    val glyph = mapOf("pickup" to "🌾", "drop" to "🏠", "partner" to "🛵")
    BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F3EC))) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val half = with(density) { 16.dp.toPx() }
        val markers = project(points, w, h)
        markers.forEach { m ->
            Box(
                Modifier.offset { IntOffset((m.x - half).roundToInt(), (m.y - half).roundToInt()) }.size(32.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Text(glyph[m.key] ?: "•", fontSize = 18.sp) }
        }
        if (markers.isEmpty()) FText("The map appears once a partner is on the way.", variant = TextVariant.Caption, modifier = Modifier.align(Alignment.Center))
    }
}

private val RATE_ERRORS = mapOf(
    "ALREADY_RATED" to "You already rated this order.",
    "ORDER_NOT_COMPLETED" to "You can rate the order once it is completed.",
    "ORDER_NOT_FOUND" to "Order not found.",
)

@Composable
private fun RatingForm(orderId: String, onRated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var score by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    FarmCard(Modifier.fillMaxWidth()) {
        FText("How was your delivery?", variant = TextVariant.H2, fontSize = 18.sp)
        Row(Modifier.padding(vertical = 8.dp)) {
            (1..5).forEach { n ->
                Text("★", fontSize = 34.sp, color = if (n <= score) Color(0xFFF59E0B) else Color(0xFFD1D5DB), modifier = Modifier.clickable { score = n }.padding(end = 6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, FarmColors.Border, RoundedCornerShape(8.dp)).padding(10.dp)) {
            if (comment.isEmpty()) Text("Add a comment (optional)", color = Color(0xFF999999), fontSize = 14.sp)
            BasicTextField(comment, { comment = it.take(500) }, minLines = 2, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
        }
        message?.let { Text(it, color = Color(0xFFC62828), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        FarmButton(if (busy) "Saving…" else "Submit rating", {
            if (score == 0) { message = "Pick 1 to 5 stars."; return@FarmButton }
            busy = true; message = null
            scope.launch {
                try {
                    val res = OrderApi.rateOrder(orderId, OrderRatingCreate(score, comment.trim().ifEmpty { null }))
                    if (res.ok) { runCatching { res.decode<OrderRating>() }; onRated() } else {
                        val msg = RATE_ERRORS[res.error()?.code ?: ""] ?: "Could not save your rating. Try again."
                        message = msg
                        if (msg.startsWith("You already")) onRated()
                    }
                } catch (_: NetworkException) {
                    message = "No connection. Check your network and try again."
                }
                busy = false
            }
        }, Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy)
    }
}

// ─── Harvest ────────────────────────────────────────────────────────────────

/** The harvest order of a reservation, or null while there is none (quality check not passed yet). */
private suspend fun findHarvestOrder(reservationId: String): OrderDetail? {
    var cursor: String? = null
    repeat(5) {
        val page = OrderApi.listOrders(cursor, limit = 100)
        page.items.find { it.reservationId == reservationId }?.let { return OrderApi.getOrder(it.id) }
        cursor = page.nextCursor ?: return null
    }
    return null
}

@Composable
fun HarvestScreen(reservationId: String, onBack: () -> Unit, onTrack: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<OrderDetail?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var option by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf(AddressForm()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val dates = remember { deliveryDates() }
    var date by remember { mutableStateOf(dates.first()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reservationId, reload) {
        while (true) {
            runCatching { findHarvestOrder(reservationId) }.onSuccess { order = it }
            loaded = true
            if (!shouldPollHarvest(harvestStageOf(order?.status, order?.harvestOption))) break
            delay(15_000)
        }
    }
    if (!loaded) return CenteredLoading()
    val o = order
    val stage = harvestStageOf(o?.status, o?.harvestOption)
    val summary = o?.let { summarise(it.subtotal, it.deliveryFee, it.balanceDue, it.payments) }

    fun confirm() {
        val opt = option ?: return
        var address: com.farm.core.api.DeliveryAddress? = null
        if (opt == "DELIVERY") {
            when (val r = validateAddress(form)) {
                is AddressResult.Invalid -> { errors = r.errors; return }
                is AddressResult.Ok -> { errors = emptyMap(); address = r.value }
            }
        }
        busy = true; message = null
        scope.launch {
            try {
                val res = OrderApi.chooseHarvest(HarvestOrderChoice(reservationId, opt, address, if (opt == "DELIVERY") date else null))
                if (res.ok) order = res.decode<OrderDetail>() else {
                    val err = parseChoiceError(res.status, res.error()?.code)
                    message = err.message
                    if (err.refresh) reload++
                }
            } catch (_: NetworkException) {
                message = "No connection. Check your network and try again."
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Your harvest", onBack)
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText(stage.text, color = FarmColors.TextLabel)
            if (o != null && summary != null) {
                FarmCard(Modifier.fillMaxWidth()) {
                    LineRow("Harvest value", formatRupees(summary.value))
                    if (o.harvestOption == "DELIVERY") LineRow("Delivery fee", formatRupees(summary.deliveryFee))
                    LineRow("Deposit paid", formatRupees(summary.depositPaid))
                    if (summary.balancePaid > 0) LineRow("Balance paid", formatRupees(summary.balancePaid))
                    if (summary.balanceDue > 0) Row { FText("Balance due on delivery", variant = TextVariant.Label, modifier = Modifier.weight(1f)); FText(formatRupees(summary.balanceDue), variant = TextVariant.Label) }
                }
            }
            if (stage == HarvestStage.CHOOSE) {
                OptionCard(option == "DELIVERY", "🚚  Deliver it to me", "Pooled doorstep delivery. The balance is paid when it arrives.") { option = "DELIVERY" }
                OptionCard(option == "BUYBACK", "💰  Buy-back", "The farmer buys your harvest and credits you. Nothing is delivered.") { option = "BUYBACK" }
                if (option == "DELIVERY") {
                    FarmCard(Modifier.fillMaxWidth()) {
                        FText("Delivery address", variant = TextVariant.Label)
                        FarmInput(form.line1, { form = form.copy(line1 = it) }, placeholder = "Street address", error = errors["line1"])
                        FarmInput(form.line2, { form = form.copy(line2 = it) }, placeholder = "Area (optional)")
                        FarmInput(form.city, { form = form.copy(city = it) }, placeholder = "City", error = errors["city"])
                        FarmInput(form.pincode, { form = form.copy(pincode = it) }, placeholder = "Pincode", error = errors["pincode"], maxLength = 6)
                        FarmInput(form.contactPhone, { form = form.copy(contactPhone = it) }, placeholder = "Phone (optional)", error = errors["contactPhone"])
                        FText("Delivery date", variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { dates.forEach { d -> Chip(d.substring(5), d == date, { date = d }) } }
                    }
                }
                message?.let { Text(it, color = Color(0xFFC62828), fontSize = 13.sp) }
                FarmButton(
                    if (busy) "Saving…" else if (option == "BUYBACK") "Sell it back" else "Confirm delivery",
                    ::confirm, Modifier.fillMaxWidth(), enabled = option != null && !busy,
                )
            }
            if (o != null && (stage == HarvestStage.DELIVERY || (stage == HarvestStage.COMPLETED && o.harvestOption == "DELIVERY"))) {
                FarmButton("Track delivery", { onTrack(o.id) }, Modifier.fillMaxWidth())
            }
            if (stage == HarvestStage.COMPLETED && summary != null && summary.credit > 0 && o?.harvestOption == "BUYBACK") {
                FarmCard(Modifier.fillMaxWidth()) {
                    FText("Credited ${formatRupees(summary.credit)}", variant = TextVariant.H2)
                    FText("The farmer bought your harvest back.", variant = TextVariant.Caption)
                }
            }
            if (stage == HarvestStage.NOT_READY) FText("This page refreshes by itself.", variant = TextVariant.Caption)
            FarmButton("Back", onBack, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
    }
}

@Composable
private fun OptionCard(selected: Boolean, title: String, text: String, onClick: () -> Unit) {
    FarmCard(
        Modifier.fillMaxWidth(), onClick = onClick,
        background = if (selected) Color(0xFFF0FDF4) else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) FarmColors.Primary else Color(0xFFE5E7EB)),
    ) {
        FText(title, variant = TextVariant.Label, fontSize = 15.sp)
        FText(text, variant = TextVariant.Caption)
    }
}
