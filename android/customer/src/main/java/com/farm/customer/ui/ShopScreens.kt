package com.farm.customer.ui

import com.farm.core.ui.rememberRefreshTick
import com.farm.core.ui.PullRefresh
import com.farm.customer.checkout.changedFields

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.farm.core.ui.IconLabel
import androidx.compose.ui.graphics.vector.ImageVector

import com.farm.customer.orders.stageText

import com.farm.customer.orders.orderNextStep

import com.farm.customer.orders.PLACED_POLL_MS

import com.farm.customer.orders.PLACED_POLL_LIMIT_MS

import com.farm.core.api.OrderApi

import androidx.compose.runtime.mutableStateMapOf

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.LazyRow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.farm.core.api.ApiException
import com.farm.core.api.Checkout
import com.farm.core.api.load
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.EmptyState
import com.farm.core.ui.ErrorState
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.FarmInput
import com.farm.core.ui.Pill
import com.farm.core.ui.ScreenHeader
import com.farm.core.ui.TextVariant
import com.farm.core.ui.hex
import com.farm.customer.browse.BrowseRepository
import com.farm.customer.browse.ProduceListing
import com.farm.customer.browse.formatDistance
import com.farm.customer.browse.formatHarvestDate
import com.farm.customer.checkout.AddressForm
import com.farm.customer.checkout.AddressResult
import com.farm.customer.checkout.ApiResult
import com.farm.customer.checkout.CartStore
import com.farm.customer.checkout.CheckoutError
import com.farm.customer.checkout.CheckoutSession
import com.farm.customer.checkout.DELIVERY_FEE_PER_ORDER
import com.farm.customer.checkout.MODE_FLEXIBLE
import com.farm.customer.checkout.MODE_IMMEDIATE
import com.farm.customer.checkout.clock12
import com.farm.customer.checkout.feeFor
import com.farm.core.api.DeliveryOptions
import com.farm.customer.orders.batchWhen
import androidx.compose.material3.RadioButton
import com.farm.customer.checkout.RealCheckoutApi
import com.farm.customer.checkout.breakdown
import com.farm.customer.checkout.estimateTotal
import com.farm.customer.checkout.formatQty
import com.farm.customer.checkout.formatRupees
import com.farm.customer.checkout.groupByFarm
import com.farm.customer.checkout.round2
import com.farm.customer.checkout.subtotal
import com.farm.customer.checkout.validateAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ListingDetailScreen(listingId: String, onBack: () -> Unit, onCart: () -> Unit) {
    val cart by CartStore.cart.collectAsState()
    var listing by remember { mutableStateOf<ProduceListing?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var qty by remember { mutableDoubleStateOf(1.0) }
    var added by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val tick = rememberRefreshTick()

    LaunchedEffect(listingId, reload, tick) {
        load { BrowseRepository.fetchDetail(listingId) }
            .onSuccess { listing = it; error = null }
            .onFailure { error = it }
        loading = false
        refreshing = false
    }
    LaunchedEffect(added) { if (added != null) { delay(4000); added = null } }

    if (loading) return CenteredLoading("Loading produce details...")
    val l = listing
    if (l == null) {
        Column(Modifier.fillMaxSize().background(FarmColors.Background).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if ((error as? ApiException)?.status == 404) {
                EmptyState("Listing not found", "This listing may have been removed or sold out.", icon = Icons.Rounded.SearchOff)
            } else {
                ErrorState(error, { loading = true; reload++ }, title = "Could not load this listing")
            }
            FarmButton("Back to Produce", onBack, Modifier.padding(top = 16.dp), variant = ButtonVariant.Secondary)
        }
        return
    }
    val photo = l.photos.firstOrNull()
    val inCart = cart.items.any { it.listingId == l.id }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Produce Details", onBack) {
            IconLabel(Icons.Rounded.ShoppingCart, "${cart.items.size}", Modifier.clickable(onClick = onCart).padding(6.dp), color = FarmColors.Primary, fontSize = 15, fontWeight = FontWeight.Bold)
        }
        PullRefresh(refreshing, { refreshing = true; reload++ }, Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (l.photos.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp)).background(FarmColors.PrimaryLight)) {
                    Text("No photos yet", color = FarmColors.TextMuted, modifier = Modifier.align(Alignment.Center))
                    if (l.isOrganic) Pill("Organic", FarmColors.Primary, Color.White, Modifier.align(Alignment.TopEnd).padding(10.dp), fontSize = 12, icon = Icons.Rounded.Eco)
                }
            } else {
                // Every photo of the listing.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(l.photos, key = { it.id }) { p ->
                        Box(Modifier.fillParentMaxWidth(if (l.photos.size == 1) 1f else 0.85f).height(230.dp).clip(RoundedCornerShape(14.dp)).background(FarmColors.PrimaryLight)) {
                            AsyncImage(p.url, "${l.cropName} photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            VerifiedBadge(p, compact = true, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                            if (l.isOrganic) Pill("Organic", FarmColors.Primary, Color.White, Modifier.align(Alignment.TopEnd).padding(10.dp), fontSize = 12, icon = Icons.Rounded.Eco)
                        }
                    }
                }
            }
            if (photo != null) {
                FarmCard(Modifier.fillMaxWidth()) {
                    FText("Photo verification", variant = TextVariant.Label, modifier = Modifier.padding(bottom = 8.dp))
                    VerifiedBadge(photo, compact = false)
                }
            }
            FarmCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        FText(l.cropName, variant = TextVariant.H1, fontSize = 26.sp)
                        l.variety?.let { FText("Variety: $it", color = FarmColors.TextLabel) }
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatRupees(l.pricePerUnit), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
                    Text(" / ${l.unit}", fontSize = 15.sp, color = FarmColors.TextMuted)
                    Spacer(Modifier.weight(1f))
                    Pill("${qtyText(l.qtyAvailable)} ${l.unit} in stock", FarmColors.PrimaryLight, FarmColors.Primary)
                }
                l.description?.let { FText(it, color = FarmColors.TextBody, modifier = Modifier.padding(top = 10.dp)) }
                // The customers' rating of the farm is the quality signal (there is no grading step).
                FarmRatingText(l.farmRating, l.farmRatingCount, Modifier.padding(top = 10.dp))
            }
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Farm & Origin", variant = TextVariant.H2, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { InfoRow(Icons.Rounded.Agriculture, "Farm Name", l.farmName) }
                    FavouriteButton(l.farmId, l.farmName)
                }
                Column(Modifier.padding(start = 34.dp)) { FarmRatingText(l.farmRating, l.farmRatingCount) }
                l.farmerName?.let { InfoRow(Icons.Rounded.Person, "Farmer", it) }
                placeOf(l)?.let { InfoRow(Icons.Rounded.LocationOn, "Village and district", it) }
                InfoRow(Icons.Rounded.Event, "Harvest Date", formatHarvestDate(l.harvestDate))
            }
            added?.let {
                Banner(it, FarmColors.PrimaryLight, FarmColors.PrimaryDark, icon = Icons.Rounded.CheckCircle) {
                    Text("View Cart", color = FarmColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onCart))
                }
            }
        }
        }
        // Sticky purchase bar
        Row(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                FText("Quantity (${l.unit})", variant = TextVariant.Caption)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("−", qty > 1) { qty = maxOf(1.0, qty - 1) }
                    Text(formatQty(qty), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                    StepButton("+", qty < l.qtyAvailable) { qty = minOf(l.qtyAvailable, qty + 1) }
                }
            }
            Column(Modifier.weight(1f)) {
                FText("Subtotal", variant = TextVariant.Caption)
                FText(formatRupees(round2(l.pricePerUnit * qty)), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
            }
            FarmButton(if (inCart) "Add More" else "Add to Cart", {
                CartStore.add(l.toCartItem(qty))
                added = "Added ${formatQty(qty)} ${l.unit} to cart"
            })
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = FarmColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column { FText(label, variant = TextVariant.Label); FText(value) }
    }
}

@Composable
fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(FarmColors.PrimaryLight).border(1.dp, FarmColors.Border, CircleShape)
            .alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary) }
}

@Composable
fun CartScreen(onBack: () -> Unit, onBrowse: () -> Unit, onCheckout: () -> Unit) {
    val cart by CartStore.cart.collectAsState()
    val groups = cart.groupByFarm()
    val notice = remember { CartStore.notice.also { CartStore.notice = null } }
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Your cart", onBack)
        notice?.let { Banner(it, FarmColors.WarningBg, FarmColors.Warning, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
        if (groups.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FText("Your cart is empty", variant = TextVariant.H2)
                FText("Add produce from nearby farms to get started.", variant = TextVariant.Caption, modifier = Modifier.padding(vertical = 8.dp))
                FarmButton("Browse produce", onBrowse)
            }
            return
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.forEach { g ->
                FarmCard(Modifier.fillMaxWidth()) {
                    FText(g.farmName, variant = TextVariant.H2, fontSize = 18.sp)
                    g.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                FText(item.name)
                                FText("${formatRupees(item.unitPrice)} / ${item.unit}", variant = TextVariant.Caption)
                            }
                            StepButton("−", true) { CartStore.setQty(item.listingId, item.qty - 1) }
                            Text(formatQty(item.qty), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            StepButton("+", item.maxQty == null || item.qty < item.maxQty) { CartStore.setQty(item.listingId, item.qty + 1) }
                            Text("Remove", fontSize = 12.sp, color = FarmColors.Error, modifier = Modifier.clickable { CartStore.remove(item.listingId) }.padding(start = 10.dp))
                        }
                    }
                    FText("Subtotal ${formatRupees(g.subtotal)}", variant = TextVariant.Label, modifier = Modifier.align(Alignment.End).padding(top = 10.dp))
                }
            }
            FText(
                "One order is placed per farm; delivery is ${formatRupees(DELIVERY_FEE_PER_ORDER)} per order, or less if you choose shared delivery at checkout.",
                variant = TextVariant.Caption,
            )
        }
        Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText("Items ${formatRupees(cart.subtotal())}", variant = TextVariant.Caption)
                FText(formatRupees(cart.estimateTotal(DELIVERY_FEE_PER_ORDER)), variant = TextVariant.H2)
            }
            FarmButton("Checkout", onCheckout)
        }
    }
}

@Composable
fun CheckoutScreen(onBack: () -> Unit, onCart: () -> Unit, onLogin: () -> Unit, onPlaced: () -> Unit) {
    val cart by CartStore.cart.collectAsState()
    val scope = rememberCoroutineScope()
    val session = remember { CheckoutSession(RealCheckoutApi, DELIVERY_FEE_PER_ORDER) }
    var mode by remember { mutableStateOf(MODE_IMMEDIATE) }
    // fees and the batch time come from the order service; the built-in fees apply until it answers (or if it is down)
    var options by remember { mutableStateOf<DeliveryOptions?>(null) }
    LaunchedEffect(Unit) { load { OrderApi.deliveryOptions() }.onSuccess { options = it } }
    val fee = feeFor(mode, options)
    var form by remember { mutableStateOf(AddressForm()) }
    var notes by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<CheckoutError?>(null) }
    val groups = cart.groupByFarm()
    val breakdown = cart.breakdown(fee)
    val total = breakdown.total

    fun placeOrder() {
        when (val r = validateAddress(form)) {
            is AddressResult.Invalid -> errors = r.errors
            is AddressResult.Ok -> scope.launch {
                submitting = true
                when (val res = session.submit(cart, r.value, notes.trim().ifEmpty { null }, mode, fee)) {
                    is ApiResult.Ok -> {
                        CartStore.lastCheckout = res.data
                        CartStore.clear()
                        onPlaced()
                    }
                    is ApiResult.Err -> error = res.error
                }
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Checkout", onBack)
        if (groups.isEmpty() && !submitting) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FText("Nothing to check out", variant = TextVariant.H2)
                FarmButton("Back to cart", onCart, Modifier.padding(top = 12.dp))
            }
            return
        }
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Order summary", variant = TextVariant.H2, fontSize = 18.sp)
                if (breakdown.farms.size > 1) {
                    FText("Your cart becomes ${breakdown.farms.size} orders, one per farm.", variant = TextVariant.Caption)
                }
                groups.forEach { g ->
                    val charge = breakdown.farms.first { it.farmId == g.farmId }
                    FText(g.farmName, variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp))
                    g.items.forEach { SummaryLine("${it.name} · ${formatQty(it.qty)} ${it.unit}", formatRupees(round2(it.unitPrice * it.qty))) }
                    SummaryLine("Delivery", formatRupees(charge.deliveryFee))
                    SummaryLine("Order total", formatRupees(charge.total), bold = true)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = FarmColors.Border)
                SummaryLine("Items", formatRupees(breakdown.items))
                SummaryLine("Delivery (${breakdown.farms.size} × ${formatRupees(fee)})", formatRupees(breakdown.delivery))
                SummaryLine("To pay", formatRupees(breakdown.total), bold = true)
                FText("Payment is simulated: no real money is taken.", variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp))
            }
            DeliveryChoiceCard(mode, { mode = it; error = null }, options)
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Delivery address", variant = TextVariant.H2, fontSize = 18.sp)
                DeliveryAddressFields(form, errors) { next ->
                    errors = errors - changedFields(form, next)
                    error = null
                    form = next
                }
                FarmInput(notes, { notes = it }, label = "Note for the farmer (optional)")
            }
            error?.let { e -> CheckoutErrorCard(e, cart, onLogin) }
            FarmButton(
                if (error is CheckoutError.PaymentFailed) "Try again · ${formatRupees(total)}" else "Place order · ${formatRupees(total)}",
                ::placeOrder, Modifier.fillMaxWidth(), loading = submitting, enabled = !submitting,
            )
        }
    }
}

/** Deliver as soon as possible, or "I can wait": the order joins the daily shared-delivery batch and costs less. */
@Composable
private fun DeliveryChoiceCard(mode: String, onMode: (String) -> Unit, options: DeliveryOptions?) {
    val clock = clock12(options?.batchTime ?: "10:00")
    val choices = listOf(
        Triple(MODE_IMMEDIATE, "Deliver as soon as possible", "On a delivery route as soon as the farmer packs your order."),
        Triple(
            MODE_FLEXIBLE,
            "I can wait: shared delivery",
            "Your order joins our daily $clock batch, where orders from nearby homes share one delivery partner. It arrives later, and costs less.",
        ),
    )
    FarmCard(Modifier.fillMaxWidth()) {
        FText("How soon do you need it?", variant = TextVariant.H2, fontSize = 18.sp)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (value, title, body) ->
                val selected = mode == value
                FarmCard(
                    Modifier.fillMaxWidth(),
                    background = if (selected) FarmColors.PrimaryLight else Color.White,
                    border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) FarmColors.Primary else FarmColors.Border),
                    padding = PaddingValues(12.dp),
                    onClick = { onMode(value) },
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        RadioButton(selected = selected, onClick = { onMode(value) })
                        Column(Modifier.weight(1f).padding(start = 4.dp, top = 12.dp)) {
                            Row {
                                FText(title, variant = TextVariant.Label, modifier = Modifier.weight(1f))
                                FText("${formatRupees(feeFor(value, options))} per farm", variant = TextVariant.Label)
                            }
                            FText(body, variant = TextVariant.Caption)
                        }
                    }
                }
            }
        }
        val next = options?.nextBatchAt?.let { batchWhen(it) }
        if (mode == MODE_FLEXIBLE && next != null) {
            FText(
                "Earliest shared run: $next. Orders the farmer packs after a run go out in the next one.",
                variant = TextVariant.Caption, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        FText(label, variant = if (bold) TextVariant.Label else TextVariant.Caption, modifier = Modifier.weight(1f))
        FText(value, variant = if (bold) TextVariant.Label else TextVariant.Caption)
    }
}

@Composable
private fun CheckoutErrorCard(e: CheckoutError, cart: com.farm.customer.checkout.Cart, onLogin: () -> Unit) {
    FarmCard(Modifier.fillMaxWidth(), background = FarmColors.ErrorBg, border = BorderStroke(1.dp, FarmColors.ErrorBg)) {
        Text(e.message, color = FarmColors.Error, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        when (e) {
            is CheckoutError.Stock -> {
                e.shortages.forEach { s ->
                    val item = cart.items.find { it.listingId == s.listingId }
                    FText(
                        "${item?.name ?: "An item"}: " + if (s.available > 0) "only ${formatQty(s.available)} ${item?.unit ?: ""} left" else "sold out",
                        variant = TextVariant.Caption,
                    )
                }
                FarmButton("Update my cart", { CartStore.adjustToStock(e.shortages) }, Modifier.padding(top = 8.dp), variant = ButtonVariant.Secondary)
            }
            is CheckoutError.ListingUnavailable -> {
                val gone = cart.items.filter { it.listingId in e.listingIds }
                gone.forEach { FText("${it.name} (${it.farmName}) is no longer for sale.", variant = TextVariant.Caption) }
                if (gone.isNotEmpty()) {
                    FarmButton(
                        if (gone.size == 1) "Remove it from my cart" else "Remove them from my cart",
                        { gone.forEach { CartStore.remove(it.listingId) } },
                        Modifier.padding(top = 8.dp), variant = ButtonVariant.Secondary,
                    )
                }
            }
            is CheckoutError.PaymentFailed -> FText("Tap Try again to pay again; it starts a fresh payment.", variant = TextVariant.Caption)
            is CheckoutError.Validation -> e.fields.forEach { (f, m) -> FText("$f: $m", variant = TextVariant.Caption) }
            is CheckoutError.Unauthorized -> FarmButton("Log in", onLogin, Modifier.padding(top = 8.dp), variant = ButtonVariant.Secondary)
            else -> {}
        }
    }
}

@Composable
fun OrderPlacedScreen(onTrack: (String) -> Unit, onOrders: () -> Unit, onShop: () -> Unit) {
    val checkout: Checkout? = remember { CartStore.lastCheckout }
    // The order service confirms payment through events a few seconds later: poll each new order
    // briefly so the status shown is the server's, not a guess.
    val statuses = remember { mutableStateMapOf<String, String>() }
    var refreshing by remember { mutableStateOf(false) }
    var reloadPlaced by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadPlaced) {
        if (reloadPlaced == 0) return@LaunchedEffect
        checkout?.orders?.forEach { o -> load { OrderApi.getOrder(o.id) }.onSuccess { statuses[o.id] = it.status } }
        refreshing = false
    }
    LaunchedEffect(checkout) {
        val orders = checkout?.orders ?: return@LaunchedEffect
        var waited = 0L
        while (waited <= PLACED_POLL_LIMIT_MS && orders.any { (statuses[it.id] ?: it.status) == "PLACED" }) {
            delay(PLACED_POLL_MS)
            waited += PLACED_POLL_MS
            orders.filter { (statuses[it.id] ?: it.status) == "PLACED" }.forEach { o ->
                load { OrderApi.getOrder(o.id) }.onSuccess { statuses[o.id] = it.status }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Order placed", null)
        if (checkout == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FText("No recent order", variant = TextVariant.H2)
                FarmButton("Back to shop", onShop, Modifier.padding(top = 12.dp))
            }
            return
        }
        PullRefresh(refreshing, { refreshing = true; reloadPlaced++ }, Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconLabel(Icons.Rounded.CheckCircle, "Order placed", color = FarmColors.TextDark, iconColor = FarmColors.Primary, fontSize = 24, fontWeight = FontWeight.Bold)
            FText(
                if (checkout.orders.size == 1) "Your order is confirmed." else "Your cart was split into ${checkout.orders.size} orders, one per farm.",
                variant = TextVariant.Caption,
            )
            checkout.orders.forEach { o ->
                FarmCard(Modifier.fillMaxWidth()) {
                    FText("Order ${o.id.take(8).uppercase()}", variant = TextVariant.Label)
                    val status = statuses[o.id] ?: o.status
                    FText(stageText(status), variant = TextVariant.Caption)
                    orderNextStep(status, o.deliveryMode, o.batchAt)?.let { FText(it, variant = TextVariant.Caption, color = FarmColors.TextLabel) }
                    o.items.forEach { FText("${it.name} · ${formatQty(it.qty)} ${it.unit}", variant = TextVariant.Caption) }
                    FText("Total ${formatRupees(o.total)}", variant = TextVariant.Label, modifier = Modifier.padding(vertical = 6.dp))
                    FarmButton("Track delivery", { onTrack(o.id) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
                }
            }
            FText("Paid ${formatRupees(checkout.total)}", variant = TextVariant.H2)
            FarmButton("View my orders", onOrders, Modifier.fillMaxWidth())
            FarmButton("Continue shopping", onShop, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
        }
        }
    }
}
