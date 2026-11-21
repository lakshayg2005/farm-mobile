package com.farm.customer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.farm.core.api.Checkout
import com.farm.core.ui.Banner
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
import com.farm.customer.browse.BrowseRepository
import com.farm.customer.browse.ProduceListing
import com.farm.customer.browse.formatDistance
import com.farm.customer.browse.formatHarvestDate
import com.farm.customer.browse.gradeMeta
import com.farm.customer.checkout.AddressForm
import com.farm.customer.checkout.AddressResult
import com.farm.customer.checkout.ApiResult
import com.farm.customer.checkout.CartStore
import com.farm.customer.checkout.CheckoutError
import com.farm.customer.checkout.CheckoutSession
import com.farm.customer.checkout.DELIVERY_FEE_PER_ORDER
import com.farm.customer.checkout.RealCheckoutApi
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
    var error by remember { mutableStateOf<String?>(null) }
    var qty by remember { mutableDoubleStateOf(1.0) }
    var added by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listingId) {
        try {
            listing = BrowseRepository.fetchDetail(listingId)
        } catch (e: Exception) {
            error = e.message ?: "Failed to load produce listing"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(added) { if (added != null) { delay(4000); added = null } }

    if (loading) return CenteredLoading("Loading produce details...")
    val l = listing
    if (l == null) {
        Column(Modifier.fillMaxSize().background(FarmColors.Background).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️", fontSize = 40.sp)
            FText("Listing not found", variant = TextVariant.H2)
            FText(error ?: "This listing may have been removed or sold out.", variant = TextVariant.Caption, textAlign = TextAlign.Center)
            FarmButton("Back to Produce", onBack, Modifier.padding(top = 16.dp))
        }
        return
    }
    val photo = l.photos.firstOrNull()
    val grade = gradeMeta(l.grade)
    val inCart = cart.items.any { it.listingId == l.id }

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Produce Details", onBack) {
            Text("🛒 ${cart.items.size}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onCart).padding(6.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF0FDF4))) {
                if (photo != null && photo.url.isNotEmpty()) {
                    AsyncImage(photo.url, l.cropName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else Text("🥕", fontSize = 64.sp, modifier = Modifier.align(Alignment.Center))
                if (l.isOrganic) Pill("🌱 100% Organic", FarmColors.Primary, Color.White, Modifier.align(Alignment.TopEnd).padding(10.dp), fontSize = 12)
            }
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Produce Photo Verification", variant = TextVariant.Label, modifier = Modifier.padding(bottom = 8.dp))
                VerifiedBadge(photo, compact = false)
            }
            FarmCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        FText(l.cropName, variant = TextVariant.H1, fontSize = 26.sp)
                        l.variety?.let { FText("Variety: $it", color = FarmColors.TextLabel) }
                    }
                    if (l.grade != null) Pill(grade.label, hex(grade.bg), hex(grade.fg), fontSize = 12)
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatRupees(l.pricePerUnit), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
                    Text(" / ${l.unit}", fontSize = 15.sp, color = FarmColors.TextMuted)
                    Spacer(Modifier.weight(1f))
                    Pill("${qtyText(l.qtyAvailable)} ${l.unit} in stock", FarmColors.PrimaryLight, FarmColors.Primary)
                }
                l.description?.let { FText(it, color = Color(0xFF444444), modifier = Modifier.padding(top = 10.dp)) }
                Text(
                    "🔍 ${grade.description}", fontSize = 12.sp, color = hex(grade.fg),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(hex(grade.bg)).padding(10.dp),
                )
            }
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Farm & Origin", variant = TextVariant.H2, fontSize = 18.sp)
                InfoRow("🏡", "Farm Name", l.farmName)
                l.farmerName?.let { InfoRow("👨‍🌾", "Farmer", it) }
                InfoRow("📍", "Location & Distance", "${l.village?.let { "$it, " } ?: ""}${l.district} (${formatDistance(l.distanceM)} away)")
                InfoRow("📅", "Harvest Date", formatHarvestDate(l.harvestDate))
            }
            added?.let {
                Banner(it, Color(0xFFE8F5E9), Color(0xFF1B5E20)) {
                    Text("View Cart →", color = FarmColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onCart))
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
                added = "✓ Added ${formatQty(qty)} ${l.unit} to cart!"
            })
        }
    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column { FText(label, variant = TextVariant.Label); FText(value) }
    }
}

@Composable
fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFF0FDF4)).border(1.dp, Color(0xFFBBF7D0), CircleShape)
            .alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary) }
}

@Composable
fun CartScreen(onBack: () -> Unit, onBrowse: () -> Unit, onCheckout: () -> Unit) {
    val cart by CartStore.cart.collectAsState()
    val groups = cart.groupByFarm()
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Your cart", onBack)
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
                            Text("Remove", fontSize = 12.sp, color = Color(0xFFC62828), modifier = Modifier.clickable { CartStore.remove(item.listingId) }.padding(start = 10.dp))
                        }
                    }
                    FText("Subtotal ${formatRupees(g.subtotal)}", variant = TextVariant.Label, modifier = Modifier.align(Alignment.End).padding(top = 10.dp))
                }
            }
            FText("One order is placed per farm; delivery is ${formatRupees(DELIVERY_FEE_PER_ORDER)} per order.", variant = TextVariant.Caption)
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
    var form by remember { mutableStateOf(AddressForm()) }
    var notes by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<CheckoutError?>(null) }
    val groups = cart.groupByFarm()
    val total = cart.estimateTotal(DELIVERY_FEE_PER_ORDER)

    fun set(field: String, update: AddressForm.(String) -> AddressForm): (String) -> Unit = { v ->
        form = form.update(v)
        errors = errors - field
        error = null
    }

    fun placeOrder() {
        when (val r = validateAddress(form)) {
            is AddressResult.Invalid -> errors = r.errors
            is AddressResult.Ok -> scope.launch {
                submitting = true
                when (val res = session.submit(cart, r.value, notes.trim().ifEmpty { null })) {
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
                groups.forEach { g ->
                    FText(g.farmName, variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp))
                    g.items.forEach { FText("${it.name} · ${formatQty(it.qty)} ${it.unit} · ${formatRupees(it.unitPrice * it.qty)}", variant = TextVariant.Caption) }
                }
                FText("Total ${formatRupees(total)} (incl. delivery)", variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp))
                FText("Payment is simulated: no real money is taken.", variant = TextVariant.Caption)
            }
            FarmCard(Modifier.fillMaxWidth()) {
                FText("Delivery address", variant = TextVariant.H2, fontSize = 18.sp)
                FarmInput(form.line1, set("line1") { copy(line1 = it) }, label = "Street address", error = errors["line1"])
                FarmInput(form.line2, set("line2") { copy(line2 = it) }, label = "Landmark (optional)")
                FarmInput(form.city, set("city") { copy(city = it) }, label = "City", error = errors["city"])
                FarmInput(form.pincode, set("pincode") { copy(pincode = it) }, label = "Pincode", error = errors["pincode"], keyboardType = KeyboardType.Number, maxLength = 6)
                FarmInput(form.contactPhone, set("contactPhone") { copy(contactPhone = it) }, label = "Phone (optional)", error = errors["contactPhone"], keyboardType = KeyboardType.Phone)
                FarmInput(form.lat, set("lat") { copy(lat = it) }, label = "Latitude", error = errors["lat"], keyboardType = KeyboardType.Decimal)
                FarmInput(form.lng, set("lng") { copy(lng = it) }, label = "Longitude", error = errors["lng"], keyboardType = KeyboardType.Decimal)
                FarmInput(notes, { notes = it }, label = "Note for the farmer (optional)")
            }
            error?.let { e -> CheckoutErrorCard(e, cart, onLogin) }
            FarmButton("Place order · ${formatRupees(total)}", ::placeOrder, Modifier.fillMaxWidth(), loading = submitting, enabled = !submitting)
        }
    }
}

@Composable
private fun CheckoutErrorCard(e: CheckoutError, cart: com.farm.customer.checkout.Cart, onLogin: () -> Unit) {
    FarmCard(Modifier.fillMaxWidth(), background = Color(0xFFFFF5F5), border = BorderStroke(1.dp, Color(0xFFFFCDD2))) {
        Text(e.message, color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
            is CheckoutError.Validation -> e.fields.forEach { (f, m) -> FText("$f: $m", variant = TextVariant.Caption) }
            is CheckoutError.Unauthorized -> FarmButton("Log in", onLogin, Modifier.padding(top = 8.dp), variant = ButtonVariant.Secondary)
            else -> {}
        }
    }
}

@Composable
fun OrderPlacedScreen(onTrack: (String) -> Unit, onOrders: () -> Unit, onShop: () -> Unit) {
    val checkout: Checkout? = remember { CartStore.lastCheckout }
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Order placed", null)
        if (checkout == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                FText("No recent order", variant = TextVariant.H2)
                FarmButton("Back to shop", onShop, Modifier.padding(top = 12.dp))
            }
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText("Order placed ✓", variant = TextVariant.H1)
            FText(
                if (checkout.orders.size == 1) "Your order is confirmed." else "Your cart was split into ${checkout.orders.size} orders, one per farm.",
                variant = TextVariant.Caption,
            )
            checkout.orders.forEach { o ->
                FarmCard(Modifier.fillMaxWidth()) {
                    FText("Order ${o.id.take(8).uppercase()}", variant = TextVariant.Label)
                    FText("Status: ${o.status.replace('_', ' ').lowercase()}", variant = TextVariant.Caption)
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
