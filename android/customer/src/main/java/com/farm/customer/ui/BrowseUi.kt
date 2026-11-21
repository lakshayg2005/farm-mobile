package com.farm.customer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.FarmInput
import com.farm.core.ui.Pill
import com.farm.core.ui.TextVariant
import com.farm.core.ui.hex
import com.farm.customer.browse.FilterState
import com.farm.customer.browse.LocationCoords
import com.farm.customer.browse.ProduceListing
import com.farm.customer.browse.ProducePhoto
import com.farm.customer.browse.SortOption
import com.farm.customer.browse.cropEmoji
import com.farm.customer.browse.formatDistance
import com.farm.customer.browse.formatHarvestDate
import com.farm.customer.browse.gradeMeta
import com.farm.customer.browse.photoVerification
import com.farm.customer.checkout.CartItem
import com.farm.customer.checkout.CartStore
import com.farm.customer.checkout.formatRupees
import com.farm.customer.orders.LatLng
import com.farm.customer.orders.project
import kotlin.math.roundToInt

fun ProduceListing.toCartItem(qty: Double = 1.0) = CartItem(
    listingId = id, farmId = farmId, farmName = farmName, name = displayName, unit = unit,
    unitPrice = pricePerUnit, qty = qty, maxQty = qtyAvailable,
)

fun qtyText(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) FarmColors.Primary else Color.White)
            .border(1.dp, if (selected) FarmColors.Primary else Color(0xFFE0E0E0), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color(0xFF444444))
    }
}

@Composable
fun VerifiedBadge(photo: ProducePhoto?, compact: Boolean, modifier: Modifier = Modifier) {
    val m = photoVerification(photo)
    if (compact) {
        Pill(m.shortLabel, hex(m.bg), hex(m.fg), modifier, border = hex(m.border))
    } else {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(hex(m.bg))
                .border(1.dp, hex(m.border), RoundedCornerShape(10.dp)).padding(12.dp),
        ) {
            Text(m.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = hex(m.fg))
            Text(m.description, fontSize = 12.sp, color = hex(m.fg), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun ListingCard(listing: ProduceListing, onOpen: () -> Unit, onAdded: (ProduceListing) -> Unit) {
    val photo = listing.photos.firstOrNull()
    val grade = gradeMeta(listing.grade)
    FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp), onClick = onOpen) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFF0FDF4))) {
            if (photo != null && photo.url.isNotEmpty()) {
                AsyncImage(model = photo.thumbUrl.ifEmpty { photo.url }, contentDescription = listing.cropName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("🥕", fontSize = 48.sp, modifier = Modifier.align(Alignment.Center))
            }
            VerifiedBadge(photo, compact = true, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            if (listing.isOrganic) Pill("🌱 Organic", Color(0xFF2D6A4F), Color.White, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    FText(listing.cropName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                    listing.variety?.let { FText(it, variant = TextVariant.Caption) }
                }
                if (listing.grade != null) Pill(grade.label, hex(grade.bg), hex(grade.fg))
            }
            FText("🏡 ${listing.farmName}", variant = TextVariant.Label, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📍 ${formatDistance(listing.distanceM)} away", fontSize = 12.sp, color = Color(0xFF40916C), fontWeight = FontWeight.SemiBold)
                Text("  •  ", fontSize = 12.sp, color = Color(0xFFBBBBBB))
                FText(formatHarvestDate(listing.harvestDate), variant = TextVariant.Caption)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatRupees(listing.pricePerUnit), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
                        Text(" / ${listing.unit}", fontSize = 13.sp, color = FarmColors.TextMuted)
                    }
                    FText("${qtyText(listing.qtyAvailable)} ${listing.unit} in stock", variant = TextVariant.Caption)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(FarmColors.Primary)
                        .clickable { CartStore.add(listing.toCartItem()); onAdded(listing) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("+ Add", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
}

/** Filters & sort sheet (port of FilterSheet.tsx). Edits a draft that is applied on "Apply". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(filters: FilterState, onApply: (FilterState) -> Unit, onReset: () -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(filters) }
    var minText by remember { mutableStateOf(filters.minPrice?.let(::qtyText) ?: "") }
    var maxText by remember { mutableStateOf(filters.maxPrice?.let(::qtyText) ?: "") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            FText("Filters & Sort", variant = TextVariant.H2)
            Section("Search Radius") {
                listOf(5, 10, 25, 50).forEach { r -> Chip("$r km", draft.radiusKm == r, { draft = draft.copy(radiusKm = r) }) }
            }
            Section("Sort By") {
                SortOption.entries.forEach { o -> Chip(o.label, draft.sort == o, { draft = draft.copy(sort = o) }) }
            }
            Section("Minimum Quality Grade") {
                listOf("ALL", "A", "B", "C").forEach { g ->
                    Chip(if (g == "ALL") "Any Grade" else "Grade $g & above", draft.minGrade == g, { draft = draft.copy(minGrade = g) })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAF8))
                    .clickable { draft = draft.copy(organicOnly = !draft.organicOnly) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    FText("🌱 Organic Certified Only", variant = TextVariant.Label)
                    FText("Only show listings grown with natural organic methods", variant = TextVariant.Caption)
                }
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (draft.organicOnly) FarmColors.Primary else Color.White)
                        .border(1.5.dp, FarmColors.Primary, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (draft.organicOnly) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            FText("Price Range (₹ per unit)", variant = TextVariant.Label, modifier = Modifier.padding(top = 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FarmInput(minText, { minText = it }, Modifier.weight(1f), placeholder = "Min (e.g. 10)", keyboardType = KeyboardType.Decimal)
                Text("  to  ", color = FarmColors.TextMuted)
                FarmInput(maxText, { maxText = it }, Modifier.weight(1f), placeholder = "Max (e.g. 100)", keyboardType = KeyboardType.Decimal)
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Reset", { onReset(); onClose() }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                FarmButton("Apply Filters", {
                    onApply(draft.copy(minPrice = minText.toDoubleOrNull(), maxPrice = maxText.toDoubleOrNull()))
                    onClose()
                }, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    FText(title, variant = TextVariant.Label, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/**
 * Pin markers placed on a plain canvas by their real coordinates around the customer.
 * (The React app also drew a canvas; no map SDK / API key is needed.)
 */
@Composable
fun <T> PinCanvas(
    center: LocationCoords,
    items: List<T>,
    coord: (T) -> LatLng,
    key: (T) -> String,
    selectedKey: String?,
    onSelect: (T) -> Unit,
    pin: @Composable (T, Boolean) -> Unit,
    banner: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().background(Color(0xFFE8F3EC))) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val points = listOf("__you" to LatLng(center.latitude, center.longitude)) + items.map { key(it) to coord(it) }
        val markers = project(points, wPx, hPx, padding = with(density) { 56.dp.toPx() }).associateBy { it.key }

        // grid
        Column(Modifier.fillMaxSize()) {
            repeat(8) { Box(Modifier.fillMaxWidth().weight(1f).border(0.5.dp, Color(0x1A2D6A4F))) }
        }
        markers["__you"]?.let { m ->
            Column(
                Modifier.offset { IntOffset((m.x - with(density) { 50.dp.toPx() }).roundToInt(), (m.y - with(density) { 20.dp.toPx() }).roundToInt()) }.width(100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x332563EB)), contentAlignment = Alignment.Center) { Text("📍", fontSize = 20.sp) }
                Text("You (${center.name})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A),
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        items.forEach { item ->
            val m = markers[key(item)] ?: return@forEach
            Box(
                Modifier.offset { IntOffset((m.x - with(density) { 36.dp.toPx() }).roundToInt(), (m.y - with(density) { 34.dp.toPx() }).roundToInt()) }
                    .width(72.dp).clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) { pin(item, key(item) == selectedKey) }
        }
        Text(
            banner, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xCC1B4332)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun PinBubble(icon: String, text: String, selected: Boolean, accent: Color = FarmColors.Primary) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) accent else Color.White)
            .border(BorderStroke(1.5.dp, accent), RoundedCornerShape(14.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 13.sp)
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else accent, maxLines = 1)
    }
}

@Composable
fun ProduceMapView(listings: List<ProduceListing>, location: LocationCoords, onOpen: (ProduceListing) -> Unit, onAdded: (ProduceListing) -> Unit) {
    var selectedId by remember { mutableStateOf(listings.firstOrNull()?.id) }
    val selected = listings.find { it.id == selectedId } ?: listings.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        PinCanvas(
            center = location, items = listings,
            coord = { LatLng(it.pickupCoordinates.getOrElse(1) { 0.0 }, it.pickupCoordinates.getOrElse(0) { 0.0 }) },
            key = { it.id }, selectedKey = selected?.id, onSelect = { selectedId = it.id },
            pin = { item, sel -> PinBubble(cropEmoji(item.cropId), formatRupees(item.pricePerUnit), sel, if (item.isOrganic && !sel) Color(0xFF15803D) else FarmColors.Primary) },
            banner = "🗺 Showing ${listings.size} farms within radius of ${location.name}",
            modifier = Modifier.weight(1f),
        )
        if (selected != null) {
            FarmCard(Modifier.fillMaxWidth().padding(12.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FText(selected.cropName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                            if (selected.isOrganic) Pill("🌱 Organic", FarmColors.PrimaryLight, FarmColors.Primary, Modifier.padding(start = 6.dp))
                        }
                        FText("🏡 ${selected.farmName} · ${selected.village ?: selected.district}", variant = TextVariant.Label)
                        FText("📍 ${formatDistance(selected.distanceM)} away  •  ${qtyText(selected.qtyAvailable)} ${selected.unit} left", variant = TextVariant.Caption)
                        VerifiedBadge(selected.photos.firstOrNull(), true, Modifier.padding(top = 6.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatRupees(selected.pricePerUnit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.PrimaryDark)
                        FText("per ${selected.unit}", variant = TextVariant.Caption)
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FarmButton("View Details", { onOpen(selected) }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                    FarmButton("+ Add to Cart", { CartStore.add(selected.toCartItem()); onAdded(selected) }, Modifier.weight(1f))
                }
            }
        }
    }
}
