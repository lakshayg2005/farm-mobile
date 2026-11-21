package com.farm.customer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.farm.customer.browse.LocationCoords
import com.farm.customer.browse.ProducePhoto
import com.farm.customer.browse.formatDistance
import com.farm.customer.checkout.formatRupees
import com.farm.customer.land.ALL_STAGES
import com.farm.customer.land.PlotItem
import com.farm.customer.land.PlotSeasonItem
import com.farm.customer.land.ReservationItem
import com.farm.customer.land.reservationEstimate
import com.farm.customer.land.stageMeta
import com.farm.customer.orders.LatLng
import kotlinx.coroutines.launch

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

@Composable
fun PlotCard(plot: PlotItem, onReserve: (PlotItem) -> Unit) {
    FarmCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FText(plot.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                FText("📍 ${plot.village}, ${plot.district} (${formatDistance(plot.distanceM)} away)", variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
            }
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0FDFA)).border(1.dp, Color(0xFF99F6E4), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(qtyText(plot.areaAcres), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                Text("Acres", fontSize = 10.sp, color = Color(0xFF0F766E))
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("🌱 Soil: ${plot.soilType}", Color(0xFFF5F5F0), Color(0xFF555555))
            Pill("💧 ${plot.waterSource}", Color(0xFFEFF6FF), Color(0xFF1D4ED8))
        }
        plot.description?.let { FText(it, maxLines = 2, color = Color(0xFF555555), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFAFAF7)).padding(10.dp)) {
            FText("Available Seasons (${plot.seasons.size})", variant = TextVariant.Label)
            plot.seasons.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🗓 ${s.season}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                        Text("Crops: ${s.allowedCrops.joinToString(", ")}", fontSize = 11.sp, color = FarmColors.TextMuted)
                    }
                    Text("${formatRupees(s.pricePerKg)}/kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FText("Farmer Partner", variant = TextVariant.Caption)
                FText("👨‍🌾 ${plot.farmerName}", variant = TextVariant.Label)
            }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0F766E)).clickable { onReserve(plot) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text("Reserve Plot →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
fun PlotMapView(plots: List<PlotItem>, location: LocationCoords, onReserve: (PlotItem) -> Unit) {
    var selectedId by remember { mutableStateOf(plots.firstOrNull()?.id) }
    val selected = plots.find { it.id == selectedId } ?: plots.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        PinCanvas(
            center = location, items = plots,
            coord = { LatLng(it.centroid.getOrElse(1) { 0.0 }, it.centroid.getOrElse(0) { 0.0 }) },
            key = { it.id }, selectedKey = selected?.id, onSelect = { selectedId = it.id },
            pin = { p, sel -> PinBubble("🌾", "${qtyText(p.areaAcres)} ac", sel, Color(0xFF0F766E)) },
            banner = "🗺 Showing ${plots.size} farm plots near ${location.name}",
            modifier = Modifier.weight(1f),
        )
        if (selected != null) {
            FarmCard(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        FText(selected.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                        FText("🏡 ${selected.village}, ${selected.district} · ${formatDistance(selected.distanceM)} away", variant = TextVariant.Caption)
                    }
                    Pill("${qtyText(selected.areaAcres)} Acres", Color(0xFFF0FDFA), Color(0xFF0F766E))
                }
                FText("🌱 ${selected.soilType}    💧 ${selected.waterSource}", fontSize = 12.sp, color = FarmColors.TextLabel, modifier = Modifier.padding(top = 6.dp))
                FText(
                    "Seasons: " + selected.seasons.joinToString(" · ") { "${it.season} (${formatRupees(it.pricePerKg)}/kg)" },
                    fontSize = 11.sp, color = FarmColors.TextMuted, modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText("Starting from", variant = TextVariant.Caption)
                        FText("${formatRupees(selected.minPrice ?: 0.0)}/kg", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    }
                    FarmButton("Reserve This Plot →", { onReserve(selected) })
                }
            }
        }
    }
}

/** Reserve farmland: season, crop and expected harvest, with the 20% deposit estimate. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReservationSheet(plot: PlotItem, onConfirm: suspend (PlotSeasonItem, String, Double) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var season by remember(plot.id) { mutableStateOf(plot.seasons.firstOrNull()) }
    var crop by remember(plot.id) { mutableStateOf(plot.seasons.firstOrNull()?.allowedCrops?.firstOrNull() ?: "tomato") }
    var qtyText by remember { mutableStateOf("100") }
    var submitting by remember { mutableStateOf(false) }
    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val (deposit, total) = reservationEstimate(season?.pricePerKg ?: 0.0, qty)

    ModalBottomSheet(onDismissRequest = { if (!submitting) onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            FText("Reserve Farmland", variant = TextVariant.H2)
            FText("${plot.title} (${qtyText(plot.areaAcres)} Acres)", variant = TextVariant.Caption)

            FText("1. Select Agricultural Season", variant = TextVariant.Label, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            plot.seasons.forEach { s ->
                val sel = season?.id == s.id
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Color(0xFFF0FDF4) else Color.White)
                        .border(1.5.dp, if (sel) FarmColors.Primary else Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
                        .clickable {
                            season = s
                            if (crop !in s.allowedCrops) crop = s.allowedCrops.firstOrNull() ?: crop
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.season, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) FarmColors.Primary else FarmColors.TextDark)
                        Text("${s.startDate} to ${s.endDate}", fontSize = 11.sp, color = FarmColors.TextMuted)
                    }
                    Text("${formatRupees(s.pricePerKg)}/kg", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) FarmColors.Primary else FarmColors.TextLabel)
                }
            }

            FText("2. Choose Crop to Cultivate", variant = TextVariant.Label, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                season?.allowedCrops?.forEach { c -> Chip("🌱 ${cap(c)}", crop == c, { crop = c }) }
            }

            FText("3. Expected Target Harvest (kg)", variant = TextVariant.Label, modifier = Modifier.padding(top = 16.dp))
            FarmInput(qtyText, { qtyText = it }, placeholder = "Enter kg (e.g. 100, 250)", keyboardType = KeyboardType.Decimal)

            FarmCard(Modifier.fillMaxWidth().padding(top = 10.dp), background = Color(0xFFF8FAF8), border = BorderStroke(1.dp, Color(0xFFDCFCE7))) {
                FText("Reservation Agreement Summary", variant = TextVariant.Label, modifier = Modifier.padding(bottom = 8.dp))
                SummaryRow("Plot Season", season?.season ?: "-")
                SummaryRow("Selected Crop", crop.uppercase())
                SummaryRow("Rate per kg", "${formatRupees(season?.pricePerKg ?: 0.0)}/kg")
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE5E7EB))
                Row(Modifier.fillMaxWidth()) {
                    FText("Estimated Total Commitment", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FText(formatRupees(total), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText("Initial Deposit (20%)", variant = TextVariant.Label, color = Color(0xFF166534))
                        FText("Due upon farmer acceptance", variant = TextVariant.Caption, color = Color(0xFF166534))
                    }
                    FText(formatRupees(deposit), variant = TextVariant.H2, color = Color(0xFF166534))
                }
            }
            FText(
                "ℹ️ The farmer prepares the land, applies organic fertilizer, and posts photo verification updates throughout the growth cycle. At harvest, you can request fresh doorstep delivery or guaranteed buy-back.",
                fontSize = 12.sp, color = Color(0xFF555555),
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFF6FF)).padding(10.dp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Cancel", onClose, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !submitting)
                FarmButton(
                    if (submitting) "Submitting..." else "Confirm Reservation",
                    {
                        val s = season ?: return@FarmButton
                        if (qty <= 0) return@FarmButton
                        submitting = true
                        scope.launch {
                            try {
                                onConfirm(s, crop, qty)
                                onClose()
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    Modifier.weight(1f), enabled = !submitting && qty > 0 && season != null,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        FText(label, variant = TextVariant.Caption, modifier = Modifier.weight(1f))
        FText(value, variant = TextVariant.Caption, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
    }
}

@Composable
private fun reservationStatusPill(status: String) {
    val (bg, fg) = when (status) {
        "HARVEST_READY" -> Color(0xFFF3E8FF) to Color(0xFF7E22CE)
        "ACTIVE" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        else -> Color(0xFFFEF3C7) to Color(0xFFB45309)
    }
    Pill(status.replace('_', ' '), bg, fg)
}

@Composable
fun MyReservationsList(reservations: List<ReservationItem>, onSelect: (ReservationItem) -> Unit, onExplore: () -> Unit) {
    if (reservations.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌾", fontSize = 44.sp)
            FText("No Active Reservations", variant = TextVariant.H2)
            FText(
                "You haven't reserved any farmland plots yet. Browse nearby plots to reserve land and track your own organic crop!",
                variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
            FarmButton("Browse Farmland Plots", onExplore, Modifier.padding(top = 14.dp))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reservations.forEach { res ->
            val latest = res.updates.lastOrNull()
            val meta = stageMeta(latest?.stage ?: "SOWN")
            FarmCard(Modifier.fillMaxWidth(), onClick = { onSelect(res) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        FText(res.cropName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                        FText("🏡 ${res.plotTitle} (${res.seasonName})", variant = TextVariant.Caption)
                    }
                    reservationStatusPill(res.status)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(hex(meta.bg)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(meta.icon, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Current Stage: ${latest?.stageTitle?.ifEmpty { null } ?: meta.label}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = hex(meta.color))
                        Text(latest?.note ?: "Field preparation underway", fontSize = 12.sp, color = Color(0xFF555555), maxLines = 1)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { FText("Target Harvest", variant = TextVariant.Caption); FText("${qtyText(res.expectedQtyKg)} kg", variant = TextVariant.Label) }
                    Column(Modifier.weight(1f)) { FText("Deposit Paid", variant = TextVariant.Caption); FText(formatRupees(res.deposit), variant = TextVariant.Label) }
                    Pill("Track Timeline →", FarmColors.PrimaryLight, FarmColors.Primary, fontSize = 12)
                }
            }
        }
    }
}

@Composable
fun CropTimelineView(reservation: ReservationItem, onClose: () -> Unit, onHarvest: (String) -> Unit) {
    val active = stageMeta(if (reservation.status == "HARVEST_READY") "HARVEST_READY" else reservation.updates.lastOrNull()?.stage ?: "SOWN")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FarmCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    FText("Reservation ID: ${reservation.id}", variant = TextVariant.Caption)
                    FText(reservation.cropName, variant = TextVariant.H1, fontSize = 24.sp)
                    FText("🏡 ${reservation.plotTitle} (${reservation.seasonName})", variant = TextVariant.Caption)
                }
                Pill(reservation.status.replace('_', ' '), hex(active.bg), hex(active.color))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) { FText("Target Harvest", variant = TextVariant.Caption); FText("${qtyText(reservation.expectedQtyKg)} kg", variant = TextVariant.Label) }
                Column(Modifier.weight(1f)) { FText("Deposit Paid", variant = TextVariant.Caption); FText(formatRupees(reservation.deposit), variant = TextVariant.Label) }
                Column(Modifier.weight(1f)) { FText("Farmer Contact", variant = TextVariant.Caption); FText(reservation.farmerName, variant = TextVariant.Label) }
            }
        }

        // Stepper
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.Top) {
            ALL_STAGES.forEachIndexed { i, stg ->
                val meta = stageMeta(stg)
                val reached = meta.step <= active.step
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                    Box(
                        Modifier.size(if (meta.step == active.step) 44.dp else 38.dp).clip(CircleShape)
                            .background(if (reached) hex(meta.color) else Color.White)
                            .border(2.dp, if (reached) hex(meta.color) else Color(0xFFD1D5DB), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(meta.icon, fontSize = 18.sp) }
                    Text(meta.label.split(" ").first(), fontSize = 10.sp, fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                        color = if (reached) FarmColors.TextDark else Color(0xFF9CA3AF), modifier = Modifier.padding(top = 4.dp))
                }
                if (i < ALL_STAGES.size - 1) {
                    Box(Modifier.weight(1f).padding(top = 20.dp).height(3.dp).background(if (meta.step < active.step) FarmColors.Primary else Color(0xFFE5E7EB)))
                }
            }
        }

        if (reservation.status == "HARVEST_READY") {
            FarmCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), background = Color(0xFFFAF5FF), border = BorderStroke(1.dp, Color(0xFFE9D5FF))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        FText("Your Crop is Ready for Harvest!", variant = TextVariant.H2, fontSize = 17.sp, color = Color(0xFF6B21A8))
                        FText("Once the inspection passes, choose how you want to receive it:", variant = TextVariant.Caption)
                    }
                }
                FarmButton("Choose delivery or buy-back", { onHarvest(reservation.id) }, Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }

        FText("Growth Timeline & Field Updates", variant = TextVariant.H2, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            reservation.updates.forEach { u ->
                val meta = stageMeta(u.stage)
                FarmCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("${meta.icon} ${u.stageTitle.ifEmpty { meta.label }}", hex(meta.bg), hex(meta.color), Modifier.weight(1f, fill = false), fontSize = 12)
                        Spacer(Modifier.weight(1f))
                        FText(u.createdAt, variant = TextVariant.Caption)
                    }
                    u.note?.let { Text("“$it”", fontStyle = FontStyle.Italic, fontSize = 14.sp, color = Color(0xFF374151), modifier = Modifier.padding(top = 8.dp)) }
                    u.photos.forEach { p ->
                        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(170.dp).clip(RoundedCornerShape(10.dp))) {
                            AsyncImage(model = p.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color(0xFFF0FDF4)))
                            VerifiedBadge(
                                ProducePhoto(p.id, p.url, p.thumbUrl, p.isGeoVerified, if (p.isGeoVerified) "VERIFIED" else "NO_GPS", p.distanceM),
                                compact = true, modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
        FarmButton("← Back to Land Plots", onClose, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), variant = ButtonVariant.Secondary)
    }
}
