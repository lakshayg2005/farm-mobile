package com.farm.customer.ui

import com.farm.core.ui.rememberRefreshTick
import com.farm.core.ui.PullRefresh
import com.farm.customer.checkout.changedFields

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.farm.core.ui.IconLabel
import androidx.compose.ui.graphics.vector.ImageVector

import com.farm.customer.orders.harvestCosts

import com.farm.core.ui.StatusChip

import com.farm.customer.checkout.CartStore

import com.farm.customer.orders.reorder

import com.farm.customer.orders.orderNextStep

import com.farm.customer.orders.canReorder

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.ApiException
import com.farm.core.api.AppNotification
import com.farm.core.api.HarvestOrderChoice
import com.farm.core.api.NetworkException
import com.farm.core.api.NotificationApi
import com.farm.core.api.OrderApi
import com.farm.core.api.OrderDetail
import com.farm.core.api.OrderRatingCreate
import com.farm.core.api.OrderSummary
import com.farm.core.api.LandApi
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
import com.farm.core.ui.LoadingSkeleton
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
import com.farm.customer.orders.addressLabel
import com.farm.customer.orders.canCancel
import com.farm.customer.orders.canRate
import com.farm.customer.orders.cancelErrorMessage
import com.farm.customer.orders.deliveryDates
import com.farm.customer.orders.distanceLabel
import com.farm.customer.orders.etaLabel
import com.farm.customer.orders.formatDate
import com.farm.customer.orders.formatDateTime
import com.farm.customer.orders.harvestStageOf
import com.farm.customer.orders.haversineKm
import com.farm.customer.notifications.NotificationTarget
import com.farm.customer.notifications.markReadLocally
import com.farm.customer.notifications.newestFirst
import com.farm.customer.notifications.notificationTarget
import com.farm.customer.orders.itemsLabel
import com.farm.customer.reservation.showHarvestChoice
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.customer.orders.orderTitle
import com.farm.customer.orders.parseChoiceError
import com.farm.customer.orders.phaseOf
import com.farm.customer.orders.project
import com.farm.customer.orders.railStage
import com.farm.customer.orders.rupees
import com.farm.customer.orders.seenLabel
import com.farm.customer.orders.shortId
import com.farm.customer.orders.shouldPoll
import com.farm.customer.orders.shouldPollHarvest
import com.farm.customer.orders.splitOrders
import com.farm.customer.orders.statusMeta
import com.farm.customer.orders.stageText
import com.farm.customer.orders.stageTimes
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
    StatusChip(m.label, hex(m.color))
}

// ─── Orders list ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onBack: (() -> Unit)?, onHome: () -> Unit, onOrder: (String) -> Unit, onNotifications: () -> Unit) =
    RequireCustomer("Please log in as a customer to view orders.", onHome) {
        val scope = rememberCoroutineScope()
        var orders by remember { mutableStateOf<List<OrderSummary>>(emptyList()) }
        var cursor by remember { mutableStateOf<String?>(null) }
        var loaded by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var refreshing by remember { mutableStateOf(false) }
        var loadingMore by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }
        var failure by remember { mutableStateOf<String?>(null) }
        var moreError by remember { mutableStateOf<String?>(null) }
        var loadedMore by remember { mutableStateOf(false) }

        // Load, then refresh the first page every 30 s while the list is open: statuses change on
        // the server (the farmer packs, logistics pools) and the rail should follow without a restart.
        val tick = rememberRefreshTick()
        LaunchedEffect(reload, tick) {
            if (refreshing) loadedMore = false // pull-to-refresh starts over from the first page
            while (true) {
                load { OrderApi.listOrders(null) }
                    .onSuccess { page ->
                        if (!loadedMore) {
                            orders = page.items
                            cursor = page.nextCursor
                        } else {
                            // older pages were loaded with "Load more": refresh the first page, keep the rest
                            val fresh = page.items.map { it.id }.toSet()
                            orders = page.items + orders.filter { it.id !in fresh }
                        }
                        failed = false
                        failure = null
                    }
                    .onFailure { e -> if (!loaded || orders.isEmpty()) { failed = true; failure = loadErrorMessage(e) } }
                loaded = true
                refreshing = false
                delay(30_000)
            }
        }

        Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
            ScreenHeader("My Orders", onBack) {
                NotificationBell(onNotifications)
            }
            PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                val (active, past) = splitOrders(orders)
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { SignedInRow(onSignedOut = onHome) }
                    if (!loaded) item { LoadingSkeleton() }
                    if (loaded && failed) item {
                        FarmCard(Modifier.fillMaxWidth()) {
                            FText("Could not load your orders", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            FText(failure ?: "Check your connection and try again.", variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                            Icon(Icons.Rounded.ShoppingCart, null, tint = FarmColors.TextMuted, modifier = Modifier.align(Alignment.CenterHorizontally).size(44.dp))
                            FText("No orders yet", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            FText("Browse produce and place your first order!", variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (cursor != null) item {
                        FarmButton(if (loadingMore) "Loading…" else "Load more orders", {
                            loadingMore = true
                            moreError = null
                            scope.launch {
                                load { OrderApi.listOrders(cursor) }
                                    .onSuccess { page ->
                                        val known = orders.map { it.id }.toSet()
                                        orders = orders + page.items.filter { it.id !in known }
                                        cursor = page.nextCursor
                                        loadedMore = true
                                    }
                                    .onFailure { moreError = loadErrorMessage(it) }
                                loadingMore = false
                            }
                        }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !loadingMore)
                    }
                    moreError?.let { item { FText(it, variant = TextVariant.Caption, color = FarmColors.Error) } }
                }
            }
        }
    }

/** Who is signed in, and the way out (sign-out clears the session and the cart). */
@Composable
private fun SignedInRow(onSignedOut: () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    val user = auth.user ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FText("Signed in as ${user.name.ifEmpty { user.phone }}", variant = TextVariant.Caption, modifier = Modifier.weight(1f))
        FarmButton("Sign out", { AuthManager.logout(); onSignedOut() }, variant = ButtonVariant.Secondary)
    }
}

@Composable
private fun OrderCard(order: OrderSummary, onClick: () -> Unit) {
    FarmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FText(orderTitle(order.orderType, order.harvestOption), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                FText("${shortId(order.id)} · Placed ${formatDate(order.createdAt)}" + if (order.deliveryMode == "FLEXIBLE") " · Shared delivery" else "", variant = TextVariant.Caption)
            }
            Text(rupees(order.total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
        }
        FText(itemsLabel(order.itemCount), variant = TextVariant.Caption, modifier = Modifier.padding(vertical = 6.dp))
        if (order.status == "CANCELLED") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(order.status)
                Spacer(Modifier.weight(1f))
                Text("›", fontSize = 22.sp, color = FarmColors.InputBorder)
            }
        } else {
            StatusRail(railStage(order.status), stageText(order.status))
            orderNextStep(order.status, order.deliveryMode, order.batchAt)?.let { FText(it, variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

// ─── Order detail ───────────────────────────────────────────────────────────

/** Loads one order; [poll] returns the delay before the next refresh, or null to stop. */
@Composable
private fun rememberOrder(
    orderId: String,
    refreshKey: Int = 0,
    onLoaded: () -> Unit = {},
    poll: ((OrderDetail) -> Long?)? = null,
): Triple<OrderDetail?, String?, Boolean> {
    var order by remember { mutableStateOf<OrderDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val tick = rememberRefreshTick()
    LaunchedEffect(orderId, refreshKey, tick) {
        while (true) {
            load { OrderApi.getOrder(orderId) }
                .onSuccess { order = it; error = null }
                .onFailure { error = if ((it as? ApiException)?.status == 404) "Order not found" else loadErrorMessage(it) }
            loaded = true
            onLoaded()
            val next = order?.let { poll?.invoke(it) } ?: break
            delay(next)
        }
    }
    return Triple(order, error, loaded)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(orderId: String, onBack: () -> Unit, onHome: () -> Unit, onTrack: (String) -> Unit, onCart: () -> Unit) =
    RequireCustomer("Access denied.", onHome) {
        val scope = rememberCoroutineScope()
        var reload by remember { mutableIntStateOf(0) }
        var refreshing by remember { mutableStateOf(false) }
        var confirmCancel by remember { mutableStateOf(false) }
        var cancelling by remember { mutableStateOf(false) }
        var cancelError by remember { mutableStateOf<String?>(null) }
        var reordering by remember { mutableStateOf(false) }
        // Refresh every 15 s while the order can still change, so packing on the website or a
        // pickup by the partner shows up without leaving the screen.
        val (order, error, loaded) = rememberOrder(orderId, reload, onLoaded = { refreshing = false }) {
            if (it.status == "COMPLETED" || it.status == "CANCELLED") null else 15_000L
        }
        if (!loaded) return@RequireCustomer CenteredLoading()

        fun cancel() {
            confirmCancel = false
            cancelling = true
            cancelError = null
            scope.launch {
                load { OrderApi.cancelOrder(orderId) }
                    .onSuccess { res ->
                        if (res.ok) reload++ else {
                            val body = res.error()
                            cancelError = cancelErrorMessage(res.status, body?.code, body?.message)
                            if (body?.code == "INVALID_STATUS_TRANSITION" || body?.code == "ORDER_CHANGED") reload++
                        }
                    }
                    .onFailure { cancelError = loadErrorMessage(it) }
                cancelling = false
            }
        }

        if (confirmCancel) {
            AlertDialog(
                onDismissRequest = { confirmCancel = false },
                title = { Text("Cancel this order?") },
                text = { Text("The farmer will not pack it and your payment is refunded.") },
                confirmButton = { TextButton(::cancel) { Text("Cancel order", color = FarmColors.Error) } },
                dismissButton = { TextButton({ confirmCancel = false }) { Text("Keep it") } },
            )
        }

        Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
            ScreenHeader("Order Detail", onBack)
            if (order == null) {
                val notFound = error == "Order not found"
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    FText(if (notFound) "Order not found" else "Could not load the order", variant = TextVariant.H2)
                    if (!notFound) {
                        error?.let { FText(it, variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
                        FarmButton("Try again", { reload++ }, Modifier.padding(top = 16.dp))
                    }
                    FarmButton("Back to Orders", onBack, Modifier.padding(top = 12.dp), variant = ButtonVariant.Secondary)
                }
                return@Column
            }
            val stage = railStage(order.status)
            PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FarmCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                FText("${orderTitle(order.orderType, order.harvestOption)} ${shortId(order.id)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                                FText("Placed ${formatDateTime(order.createdAt)}", variant = TextVariant.Caption)
                                order.deliveryDate?.let { FText("Delivery by ${formatDate(it)}" + if (order.deliveryMode == "FLEXIBLE") " · Shared delivery" else "", variant = TextVariant.Caption) }
                            }
                            Text(rupees(order.total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                        }
                        if (order.status != "CANCELLED") StatusRail(stage, stageText(order.status), Modifier.padding(top = 12.dp))
                        orderNextStep(order.status, order.deliveryMode, order.batchAt)?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.TextLabel, modifier = Modifier.padding(top = 4.dp)) }
                        Column(Modifier.padding(top = 10.dp)) {
                            order.items.forEach { LineRow("${it.name}  ×${qtyText(it.qty)} ${it.unit}", rupees(it.lineTotal)) }
                            if (order.deliveryFee > 0) LineRow("Delivery", rupees(order.deliveryFee))
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = FarmColors.Border)
                            Row { FText("Total", variant = TextVariant.Label, modifier = Modifier.weight(1f)); FText(rupees(order.total), variant = TextVariant.Label, color = FarmColors.Primary) }
                        }
                        addressLabel(order.deliveryAddress)?.let {
                            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(FarmColors.Background).padding(10.dp)) {
                                FText("Delivery Address", variant = TextVariant.Label)
                                IconLabel(Icons.Rounded.LocationOn, it, fontSize = 12, iconColor = FarmColors.Primary)
                            }
                        }
                    }
                    FText("Order Timeline", variant = TextVariant.Label)
                    FarmCard(Modifier.fillMaxWidth()) {
                        if (order.status == "CANCELLED") {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                FText("Cancelled", variant = TextVariant.H2, color = FarmColors.Error)
                                val refunded = order.payments.filter { it.kind == "REFUND" && it.status == "SUCCEEDED" }.sumOf { it.amount }
                                FText(
                                    if (refunded > 0) "This order was cancelled. ${rupees(refunded)} was refunded to you."
                                    else "This order was cancelled. Any payment is refunded to you.",
                                    variant = TextVariant.Caption, textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            VerticalRail(stage, stageTimes(order.events))
                        }
                    }
                    order.rating?.let { r ->
                        FarmCard(Modifier.fillMaxWidth()) {
                            YourRating(r.score)
                            r.comment?.let { FText(it, variant = TextVariant.Caption) }
                        }
                    }
                    when {
                        order.status == "CANCELLED" -> {}
                        canRate(order.status, order.rating != null) -> FarmButton("Rate this order", { onTrack(order.id) }, Modifier.fillMaxWidth())
                        else -> FarmButton("Track delivery", { onTrack(order.id) }, Modifier.fillMaxWidth())
                    }
                    if (canReorder(order.orderType, order.items)) {
                        FarmButton(
                            if (reordering) "Checking today's stock…" else "Order again",
                            {
                                reordering = true
                                scope.launch {
                                    val result = reorder(order.items)
                                    result.added.forEach(CartStore::add)
                                    CartStore.notice = result.message
                                    reordering = false
                                    onCart()
                                }
                            },
                            Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !reordering,
                        )
                    }
                    if (canCancel(order.status)) {
                        cancelError?.let { Text(it, color = FarmColors.Error, fontSize = 13.sp) }
                        FarmButton(
                            if (cancelling) "Cancelling…" else "Cancel order", { confirmCancel = true },
                            Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !cancelling,
                        )
                    }
                }
            }
        }
    }

/** "Your rating" and the score as five star icons. */
@Composable
private fun YourRating(score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Your rating: $score of 5" }) {
        FText("Your rating", variant = TextVariant.Label)
        Spacer(Modifier.width(8.dp))
        (1..5).forEach { n ->
            Icon(if (n <= score) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = FarmColors.Warning, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LineRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        FText(label, variant = TextVariant.Caption, color = FarmColors.TextBody, modifier = Modifier.weight(1f))
        FText(value, variant = TextVariant.Caption, color = FarmColors.TextBody)
    }
}

// ─── Notifications ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOrder: (String) -> Unit,
    /** Opens a reservation; null while the app has no reservation screen to open. */
    onReservation: ((String) -> Unit)? = null,
) =
    RequireCustomer("Please log in as a customer.", onHome) {
        val scope = rememberCoroutineScope()
        var items by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
        var unread by remember { mutableIntStateOf(0) }
        var cursor by remember { mutableStateOf<String?>(null) }
        var loaded by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<String?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }
        var unreadOnly by remember { mutableStateOf(false) }
        var actionError by remember { mutableStateOf<String?>(null) }
        var loadingMore by remember { mutableStateOf(false) }

        val tick = rememberRefreshTick()
        LaunchedEffect(reload, unreadOnly, tick) {
            load { NotificationApi.list(null, unreadOnly = unreadOnly) }
                .onSuccess { page ->
                    items = newestFirst(page.items); unread = page.unreadCount; cursor = page.nextCursor; failure = null
                }
                .onFailure { failure = loadErrorMessage(it) }
            loaded = true; refreshing = false
        }

        /** Marks read on screen first, puts it back (and says so) if the server refuses. */
        fun markRead(id: String?) {
            val before = items to unread
            items = markReadLocally(items, id, Instant.now().toString())
            unread = if (id == null) 0 else maxOf(0, unread - 1)
            actionError = null
            scope.launch {
                load { if (id == null) NotificationApi.markAllRead() else NotificationApi.markRead(id) }
                    .onFailure {
                        items = before.first; unread = before.second
                        actionError = loadErrorMessage(it)
                    }
            }
        }

        Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
            ScreenHeader(if (unread > 0) "Notifications ($unread)" else "Notifications", onBack) {
                if (unread > 0) Text("Mark all read", color = FarmColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { markRead(null) }.padding(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", !unreadOnly, { if (unreadOnly) { unreadOnly = false; loaded = false } })
                Chip("Unread only", unreadOnly, { if (!unreadOnly) { unreadOnly = true; loaded = false } })
            }
            actionError?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.Error, modifier = Modifier.padding(horizontal = 16.dp)) }
            PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        !loaded -> item { LoadingSkeleton() }
                        failure != null && items.isEmpty() -> item {
                            FarmCard(Modifier.fillMaxWidth()) {
                                FText("Could not load notifications", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                FText(failure ?: "", variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                FarmButton("Try again", { reload++ }, Modifier.fillMaxWidth().padding(top = 16.dp))
                            }
                        }
                        items.isEmpty() -> item {
                            FarmCard(Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.NotificationsNone, null, tint = FarmColors.TextMuted, modifier = Modifier.align(Alignment.CenterHorizontally).size(44.dp))
                                FText(if (unreadOnly) "No unread notifications" else "All caught up!", variant = TextVariant.H2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                FText(
                                    if (unreadOnly) "You have read everything. Switch to All to see older ones."
                                    else "No notifications yet. We'll let you know when something happens.",
                                    variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        else -> items(items, key = { it.id }) { n ->
                            val isUnread = n.readAt == null
                            val target = when (val t = notificationTarget(n)) {
                                is NotificationTarget.Order -> { { onOrder(t.orderId) } }
                                is NotificationTarget.Reservation -> onReservation?.let { open -> { open(t.reservationId) } }
                                null -> null
                            }
                            FarmCard(
                                Modifier.fillMaxWidth(),
                                background = if (isUnread) FarmColors.PrimaryLight else Color.White,
                                border = if (isUnread) BorderStroke(1.dp, FarmColors.PrimaryLight) else null,
                                onClick = { if (isUnread) markRead(n.id); target?.invoke() },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isUnread) { Box(Modifier.size(8.dp).clip(CircleShape).background(FarmColors.Primary)); Spacer(Modifier.width(10.dp)) }
                                    Column(Modifier.weight(1f)) {
                                        FText(
                                            n.title, variant = TextVariant.Label,
                                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isUnread) FarmColors.TextDark else FarmColors.TextLabel,
                                        )
                                        FText(n.body, variant = TextVariant.Caption, color = FarmColors.TextMuted)
                                        FText((if (isUnread) "Unread · " else "") + timeAgo(n.createdAt), variant = TextVariant.Caption, fontSize = 11.sp)
                                    }
                                    if (target != null) Text("›", fontSize = 22.sp, color = FarmColors.InputBorder)
                                }
                            }
                        }
                    }
                    if (loaded && cursor != null) item {
                        FarmButton(if (loadingMore) "Loading…" else "Load older notifications", {
                            loadingMore = true
                            scope.launch {
                                load { NotificationApi.list(cursor, unreadOnly = unreadOnly) }
                                    .onSuccess { items = newestFirst(items + it.items); cursor = it.nextCursor }
                                    .onFailure { actionError = loadErrorMessage(it) }
                                loadingMore = false
                            }
                        }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !loadingMore)
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
    var refreshing by remember { mutableStateOf(false) }
    val tick = rememberRefreshTick()
    val (order, _, loaded) = rememberOrder(orderId, refresh, onLoaded = { refreshing = false }) { if (it.status == "COMPLETED" || it.status == "CANCELLED") null else 15_000L }
    var track by remember { mutableStateOf<Track?>(null) }
    val latestOrder by androidx.compose.runtime.rememberUpdatedState(order)
    LaunchedEffect(orderId, refresh, tick) {
        while (true) {
            load { LogisticsApi.track(orderId) }.onSuccess { track = it } // keep the last position on a failed poll
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

        PullRefresh(refreshing, { refreshing = true; refresh++ }, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText(phase.text, variant = TextVariant.H2, fontSize = 18.sp)
            val dropStop = t?.stop
            val otp = dropStop?.otp
            if (live && dropStop != null && otp != null && !dropStop.otpVerified) {
                FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight, border = BorderStroke(1.dp, FarmColors.PrimaryLight)) {
                    FText("Your delivery code", variant = TextVariant.Label)
                    Text(
                        otp, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = FarmColors.PrimaryDark,
                        modifier = Modifier.semantics { contentDescription = "Delivery code ${otp.toList().joinToString(" ")}" },
                    )
                    FText("Give this code to the delivery partner when your order arrives. Do not share it before.", variant = TextVariant.Caption)
                }
            }
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
                    YourRating(r.score)
                    r.comment?.let { FText(it, variant = TextVariant.Caption) }
                }
            }
            if (canRate(order.status, order.rating != null)) RatingForm(order.id) { refresh++ }
            FarmButton("Back to orders", onOrders, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
        }
    }
}

/** A plain canvas with pickup / drop / partner glyphs placed by their real coordinates. */
@Composable
private fun TrackingMap(partner: LatLng?, drop: LatLng?, pickup: LatLng?) {
    val points = listOfNotNull(pickup?.let { "pickup" to it }, drop?.let { "drop" to it }, partner?.let { "partner" to it })
    val glyph = mapOf("pickup" to Icons.Rounded.Agriculture, "drop" to Icons.Rounded.Home, "partner" to Icons.Rounded.TwoWheeler)
    val tint = mapOf("pickup" to com.farm.core.ui.PinColors.Pickup, "drop" to com.farm.core.ui.PinColors.Drop, "partner" to com.farm.core.ui.PinColors.Me)
    BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(FarmColors.PrimaryLight)) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val half = with(density) { 16.dp.toPx() }
        val markers = project(points, w, h)
        markers.forEach { m ->
            Box(
                Modifier.offset { IntOffset((m.x - half).roundToInt(), (m.y - half).roundToInt()) }.size(32.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(glyph[m.key] ?: Icons.Rounded.Place, null, tint = tint[m.key] ?: FarmColors.TextDark, modifier = Modifier.size(20.dp)) }
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
                Icon(
                    if (n <= score) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Rate $n of 5",
                    tint = if (n <= score) FarmColors.Warning else FarmColors.InputBorder,
                    modifier = Modifier.clickable { score = n }.padding(end = 6.dp).size(38.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, FarmColors.Border, RoundedCornerShape(8.dp)).padding(10.dp)) {
            if (comment.isEmpty()) Text("Add a comment (optional)", color = FarmColors.TextMuted, fontSize = 14.sp)
            BasicTextField(comment, { comment = it.take(500) }, minLines = 2, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
        }
        message?.let { Text(it, color = FarmColors.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        FarmButton(if (busy) "Saving…" else "Submit rating", {
            if (score == 0) { message = "Pick 1 to 5 stars."; return@FarmButton }
            busy = true; message = null
            scope.launch {
                try {
                    val res = OrderApi.rateOrder(orderId, OrderRatingCreate(score, comment.trim().ifEmpty { null }))
                    if (res.ok) onRated() else {
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

/** The harvest order of a reservation, or null while there is none yet (the harvest is not ready). */
private suspend fun findHarvestOrder(reservationId: String): OrderDetail? {
    var cursor: String? = null
    repeat(5) {
        val page = OrderApi.listOrders(cursor, limit = 100)
        page.items.find { it.reservationId == reservationId }?.let { return OrderApi.getOrder(it.id) }
        cursor = page.nextCursor ?: return null
    }
    return null
}

/**
 * Delivery or buy-back for a ready harvest (#43). The choice is offered
 * only while land shows the reservation as FULFILLING and the harvest order has no option yet.
 * DELIVERY then follows the normal order, tracking and rating screens; BUYBACK shows the credit.
 */
@Composable
fun HarvestScreen(reservationId: String, onBack: () -> Unit, onTrack: (String) -> Unit, onOrder: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<OrderDetail?>(null) }
    var reservationStatus by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var option by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf(AddressForm()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val dates = remember { deliveryDates() }
    var date by remember { mutableStateOf(dates.first()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val tick = rememberRefreshTick()

    LaunchedEffect(reservationId, reload, tick) {
        while (true) {
            load { LandApi.getMyReservations().find { it.id == reservationId }?.status }
                .onSuccess { reservationStatus = it }
            load { findHarvestOrder(reservationId) }
                .onSuccess { order = it; loadError = null }
                .onFailure { loadError = loadErrorMessage(it) }
            loaded = true
            refreshing = false
            if (!shouldPollHarvest(harvestStageOf(order?.status, order?.harvestOption))) break
            delay(15_000)
        }
    }
    if (!loaded) return CenteredLoading()
    val o = order
    val stage = harvestStageOf(o?.status, o?.harvestOption)
    val summary = o?.let { summarise(it.subtotal, it.deliveryFee, it.balanceDue, it.payments) }
    val choosing = showHarvestChoice(reservationStatus, stage)

    fun confirm() {
        val opt = option ?: return
        if (busy) return // one request at a time; the server also answers a repeat with the same order
        var address: com.farm.core.api.DeliveryAddress? = null
        if (opt == "DELIVERY") {
            when (val r = validateAddress(form)) {
                is AddressResult.Invalid -> { errors = r.errors; return }
                is AddressResult.Ok -> { errors = emptyMap(); address = r.value }
            }
        }
        busy = true; message = null
        scope.launch {
            load { OrderApi.chooseHarvest(HarvestOrderChoice(reservationId, opt, address, if (opt == "DELIVERY") date else null)) }
                .onSuccess { res ->
                    if (res.ok) {
                        load { res.decode<OrderDetail>() }.onSuccess { order = it }.onFailure { reload++ }
                    } else {
                        val err = parseChoiceError(res.status, res.error()?.code)
                        message = err.message
                        if (err.refresh) reload++
                    }
                }
                .onFailure { message = loadErrorMessage(it) }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Your harvest", onBack)
        PullRefresh(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (o == null && loadError != null) {
                FarmCard(Modifier.fillMaxWidth()) {
                    FText("Could not load your harvest", variant = TextVariant.H2, fontSize = 18.sp)
                    FText(loadError ?: "", variant = TextVariant.Caption)
                    FarmButton("Try again", { reload++ }, Modifier.padding(top = 8.dp))
                }
            } else {
                FText(
                    if (stage == HarvestStage.CHOOSE && !choosing) "Your harvest is ready. Getting your options ready…" else stage.text,
                    color = FarmColors.TextLabel,
                )
            }
            if (o != null && summary != null) {
                FarmCard(Modifier.fillMaxWidth()) {
                    LineRow("Harvest value", formatRupees(summary.value))
                    if (o.harvestOption == "DELIVERY") LineRow("Delivery fee", formatRupees(summary.deliveryFee))
                    LineRow("Deposit paid", formatRupees(summary.depositPaid))
                    if (summary.balancePaid > 0) LineRow("Balance paid", formatRupees(summary.balancePaid))
                    if (summary.balanceDue > 0) Row { FText("Balance due on delivery", variant = TextVariant.Label, modifier = Modifier.weight(1f)); FText(formatRupees(summary.balanceDue), variant = TextVariant.Label) }
                }
            }
            if (choosing) {
                val costs = o?.let { harvestCosts(it.total, it.balanceDue, it.deliveryFee) }
                OptionCard(
                    option == "DELIVERY", Icons.Rounded.LocalShipping, "Deliver it to me",
                    "Pooled doorstep delivery." + (costs?.let { " You pay ${formatRupees(it.deliveryPayOnArrival)} when it arrives (balance plus delivery)." } ?: " The balance is paid when it arrives."),
                ) { option = "DELIVERY"; message = null }
                OptionCard(
                    option == "BUYBACK", Icons.Rounded.CurrencyRupee, "Buy-back",
                    costs?.let { "You pay the balance of ${formatRupees(it.buybackPayNow)} now and are credited ${formatRupees(it.buybackCredit)} (90 % of the total) at once. Nothing is delivered." }
                        ?: "The farmer buys your harvest back and credits you 90 % of its value at once. Nothing is delivered.",
                ) { option = "BUYBACK"; message = null }
                if (option == "DELIVERY") {
                    FarmCard(Modifier.fillMaxWidth()) {
                        FText("Delivery address", variant = TextVariant.Label)
                        DeliveryAddressFields(form, errors) { next -> errors = errors - changedFields(form, next); form = next }
                        FText("Delivery date", variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { dates.forEach { d -> Chip(d.substring(5), d == date, { date = d }) } }
                    }
                }
                message?.let { Text(it, color = FarmColors.Error, fontSize = 13.sp) }
                FarmButton(
                    if (busy) "Saving…" else if (option == "BUYBACK") "Sell it back" else "Confirm delivery",
                    ::confirm, Modifier.fillMaxWidth(), loading = busy, enabled = option != null && !busy,
                )
            }
            if (o != null && o.harvestOption == "DELIVERY" && stage != HarvestStage.CANCELLED) {
                FarmButton("Track delivery", { onTrack(o.id) }, Modifier.fillMaxWidth())
                FarmButton("Open the order", { onOrder(o.id) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
            }
            if (o != null && summary != null && stage == HarvestStage.COMPLETED && o.harvestOption == "BUYBACK") {
                FarmCard(Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight, border = BorderStroke(1.dp, FarmColors.PrimaryLight)) {
                    FText("Credited ${formatRupees(summary.credit)}", variant = TextVariant.H2)
                    FText("The farmer bought your harvest back. The credit is recorded on the order.", variant = TextVariant.Caption)
                }
                FarmButton("Open the order", { onOrder(o.id) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
            }
            if (stage == HarvestStage.NOT_READY || (stage == HarvestStage.CHOOSE && !choosing)) {
                FText("This page refreshes by itself.", variant = TextVariant.Caption)
            }
            FarmButton("Back", onBack, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
        }
    }
}

@Composable
private fun OptionCard(selected: Boolean, icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    FarmCard(
        Modifier.fillMaxWidth(), onClick = onClick,
        background = if (selected) FarmColors.PrimaryLight else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) FarmColors.Primary else FarmColors.Border),
    ) {
        IconLabel(icon, title, color = FarmColors.TextDark, iconColor = FarmColors.Primary, fontSize = 15, fontWeight = FontWeight.SemiBold)
        FText(text, variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
    }
}
