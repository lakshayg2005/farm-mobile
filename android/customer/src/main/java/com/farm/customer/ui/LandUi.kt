package com.farm.customer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.farm.core.ui.IconLabel

import com.farm.customer.browse.NEAR_ME

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.farm.customer.land.PlotItem
import com.farm.customer.reservation.RESERVATION_STAGES
import com.farm.customer.reservation.isClosed
import com.farm.customer.reservation.nextStepText
import com.farm.customer.reservation.reservationStage
import com.farm.customer.land.LandRepository
import com.farm.customer.land.cropTitle
import com.farm.customer.land.reserveErrorMessage
import com.farm.customer.land.seasonEnded
import com.farm.core.api.ApiException
import com.farm.core.api.PlotOut
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.customer.land.PlotSeasonItem
import com.farm.customer.land.ReservationItem
import com.farm.customer.land.reservationEstimate
import kotlinx.coroutines.launch

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/** "Village, District · 2.3 km away", leaving out what the API did not return. */
private fun placeLine(plot: PlotItem): String =
    (listOf(plot.village, plot.district).filter { it.isNotEmpty() } + "${formatDistance(plot.distanceM)} away").joinToString(" · ")

@Composable
fun PlotCard(plot: PlotItem, onReserve: (PlotItem) -> Unit) {
    FarmCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FText(plot.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                IconLabel(Icons.Rounded.LocationOn, placeLine(plot), Modifier.padding(top = 2.dp), fontSize = 12, iconColor = FarmColors.Primary)
            }
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).background(FarmColors.PrimaryLight).border(1.dp, FarmColors.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(qtyText(plot.areaAcres), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                Text("Acres", fontSize = 10.sp, color = FarmColors.Primary)
            }
        }
        if (plot.soilType != null || plot.waterSource != null) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plot.soilType?.let { Pill("Soil: $it", FarmColors.Background, FarmColors.TextMuted, icon = Icons.Rounded.Grass) }
                plot.waterSource?.let { Pill(it, FarmColors.InfoBg, FarmColors.Info, icon = Icons.Rounded.WaterDrop) }
            }
        }
        plot.description?.let { FText(it, maxLines = 2, color = FarmColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(FarmColors.Background).padding(10.dp)) {
            FText("Available Seasons (${plot.seasons.size})", variant = TextVariant.Label)
            plot.seasons.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        IconLabel(Icons.Rounded.Event, s.season, color = FarmColors.TextDark, iconColor = FarmColors.Primary, fontWeight = FontWeight.SemiBold)
                        Text("Crops: ${s.allowedCrops.joinToString(", ")}", fontSize = 11.sp, color = FarmColors.TextMuted)
                    }
                    Text("${formatRupees(s.pricePerKg)}/kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (plot.farmerName.isNotEmpty()) {
                    FText("Farmer", variant = TextVariant.Caption)
                    IconLabel(Icons.Rounded.Person, plot.farmerName, color = FarmColors.TextDark, fontSize = 14, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(FarmColors.Primary).clickable { onReserve(plot) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text("Reserve Plot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
fun PlotMapView(
    plots: List<PlotItem>,
    location: LocationCoords,
    onOpen: (PlotItem) -> Unit,
    onSearchArea: (List<Double>) -> Unit,
) {
    var selectedId by remember { mutableStateOf(plots.firstOrNull()?.id) }
    val selected = plots.find { it.id == selectedId } ?: plots.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        PlotTileMap(
            plots, location.latitude, location.longitude, selected?.id,
            onSelect = { selectedId = it.id }, onSearchArea = onSearchArea, modifier = Modifier.weight(1f),
            meTitle = if (location.name == NEAR_ME) "You are here" else "Searching around ${location.name}",
        )
        if (selected != null) {
            FarmCard(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        FText(selected.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                        FText(placeLine(selected), variant = TextVariant.Caption)
                    }
                    Pill("${qtyText(selected.areaAcres)} Acres", FarmColors.PrimaryLight, FarmColors.Primary)
                }
                FText(
                    "Seasons: " + selected.seasons.joinToString(" · ") { "${it.season} (${formatRupees(it.pricePerKg)}/kg)" },
                    fontSize = 11.sp, color = FarmColors.TextMuted, modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText("Starting from", variant = TextVariant.Caption)
                        FText("${formatRupees(selected.minPrice ?: 0.0)}/kg", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                    }
                    FarmButton("View plot →", { onOpen(selected) })
                }
            }
        }
    }
}

/**
 * One plot: what the search returned (area, open seasons with price and crops) plus its
 * description, soil and water from GET /land/plots/{id}. Rows the API does not return are hidden.
 */
@Composable
fun PlotDetailView(plot: PlotItem, onReserve: (PlotSeasonItem) -> Unit, onClose: () -> Unit) {
    var detail by remember(plot.id) { mutableStateOf<PlotOut?>(null) }
    var detailError by remember(plot.id) { mutableStateOf<Throwable?>(null) }
    var reload by remember(plot.id) { mutableIntStateOf(0) }
    LaunchedEffect(plot.id, reload) {
        load { LandRepository.plotDetail(plot.id) }.onSuccess { detail = it; detailError = null }.onFailure { detailError = it }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FarmCard(Modifier.fillMaxWidth()) {
            FText(plot.title, variant = TextVariant.H1, fontSize = 24.sp)
            IconLabel(Icons.Rounded.LocationOn, placeLine(plot), fontSize = 12, iconColor = FarmColors.Primary)
            FText("${qtyText(plot.areaAcres)} acres", variant = TextVariant.Label, modifier = Modifier.padding(top = 6.dp))
            val d = detail
            when {
                d != null -> {
                    d.soilType?.let { IconLabel(Icons.Rounded.Grass, "Soil: $it", color = FarmColors.TextBody, iconColor = FarmColors.Primary) }
                    d.waterSource?.let { IconLabel(Icons.Rounded.WaterDrop, "Water: $it", color = FarmColors.TextBody, iconColor = FarmColors.Info) }
                    d.description?.takeIf { it.isNotBlank() }?.let { FText(it, modifier = Modifier.padding(top = 6.dp)) }
                }
                detailError != null -> {
                    FText("Could not load the plot's description: ${loadErrorMessage(detailError)}", variant = TextVariant.Caption)
                    FarmButton("Retry", { reload++ }, Modifier.padding(top = 6.dp), variant = ButtonVariant.Secondary)
                }
                else -> FText("Loading the plot's details…", variant = TextVariant.Caption)
            }
        }
        FText("Open seasons", variant = TextVariant.H2, fontSize = 18.sp)
        plot.seasons.forEach { s ->
            FarmCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText(s.season, variant = TextVariant.Label)
                        FText("${formatDay(s.startDate)} to ${formatDay(s.endDate)}", variant = TextVariant.Caption)
                    }
                    FText("${formatRupees(s.pricePerKg)}/kg", fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                }
                if (s.allowedCrops.isNotEmpty()) {
                    FText("Crops: " + s.allowedCrops.joinToString(", ") { cropTitle(it) }, variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp))
                }
                FarmButton("Reserve ${s.season}", { onReserve(s) }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        FarmButton("← Back to plots", onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
    }
}

private fun formatDay(iso: String): String =
    runCatching { java.time.LocalDate.parse(iso.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrDefault(iso)

/** Reserve farmland: season, crop and expected harvest, with the 20% deposit estimate. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReservationSheet(
    plot: PlotItem,
    onConfirm: suspend (PlotSeasonItem, String, Double) -> Unit,
    onClose: () -> Unit,
    initialSeasonId: String? = null,
) {
    val scope = rememberCoroutineScope()
    // ended seasons are never offered (the server would answer SEASON_ENDED)
    val openSeasons = remember(plot.id) { plot.seasons.filterNot { seasonEnded(it.endDate) } }
    var season by remember(plot.id) { mutableStateOf(openSeasons.find { it.id == initialSeasonId } ?: openSeasons.firstOrNull()) }
    var crop by remember(plot.id) { mutableStateOf(season?.allowedCrops?.firstOrNull() ?: "") }
    var qtyText by remember { mutableStateOf("100") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val (deposit, total) = reservationEstimate(season?.pricePerKg ?: 0.0, qty)

    ModalBottomSheet(onDismissRequest = { if (!submitting) onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            FText("Reserve Farmland", variant = TextVariant.H2)
            FText("${plot.title} (${qtyText(plot.areaAcres)} Acres)", variant = TextVariant.Caption)

            FText("1. Select Agricultural Season", variant = TextVariant.Label, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            if (openSeasons.isEmpty()) FText("No season of this plot is open for reservations.", variant = TextVariant.Caption)
            openSeasons.forEach { s ->
                val sel = season?.id == s.id
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) FarmColors.PrimaryLight else Color.White)
                        .border(1.5.dp, if (sel) FarmColors.Primary else FarmColors.Border, RoundedCornerShape(10.dp))
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
                season?.allowedCrops?.forEach { c -> Chip(cap(c), crop == c, { crop = c }, icon = Icons.Rounded.Eco) }
            }

            FText("3. Expected Target Harvest (kg)", variant = TextVariant.Label, modifier = Modifier.padding(top = 16.dp))
            FarmInput(qtyText, { qtyText = it }, placeholder = "Enter kg (e.g. 100, 250)", keyboardType = KeyboardType.Decimal)

            FarmCard(Modifier.fillMaxWidth().padding(top = 10.dp), background = FarmColors.Background, border = BorderStroke(1.dp, FarmColors.PrimaryLight)) {
                FText("Reservation Agreement Summary", variant = TextVariant.Label, modifier = Modifier.padding(bottom = 8.dp))
                SummaryRow("Plot Season", season?.season ?: "-")
                SummaryRow("Selected Crop", if (crop.isEmpty()) "-" else cropTitle(crop))
                SummaryRow("Rate per kg", "${formatRupees(season?.pricePerKg ?: 0.0)}/kg")
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = FarmColors.Border)
                Row(Modifier.fillMaxWidth()) {
                    FText("Estimated Total Commitment", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    FText(formatRupees(total), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FText("Initial Deposit (20%)", variant = TextVariant.Label, color = FarmColors.PrimaryDark)
                        FText("Due upon farmer acceptance", variant = TextVariant.Caption, color = FarmColors.PrimaryDark)
                    }
                    FText(formatRupees(deposit), variant = TextVariant.H2, color = FarmColors.PrimaryDark)
                }
            }
            FText(
                "ℹ️ The farmer prepares the land, applies organic fertilizer, and posts photo verification updates throughout the growth cycle. At harvest, you can request fresh doorstep delivery or guaranteed buy-back.",
                fontSize = 12.sp, color = FarmColors.TextMuted,
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).background(FarmColors.InfoBg).padding(10.dp),
            )
            error?.let { Text(it, color = FarmColors.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Cancel", onClose, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !submitting)
                FarmButton(
                    if (submitting) "Submitting..." else "Confirm Reservation",
                    {
                        val s = season ?: return@FarmButton
                        if (qty <= 0) return@FarmButton
                        submitting = true
                        error = null
                        scope.launch {
                            // a refused reservation shows the server's reason and creates nothing
                            load { onConfirm(s, crop, qty) }
                                .onSuccess { onClose() }
                                .onFailure { e -> error = (e as? ApiException)?.let { reserveErrorMessage(it.code, it.message) } ?: loadErrorMessage(e) }
                            submitting = false
                        }
                    },
                    Modifier.weight(1f), enabled = !submitting && qty > 0 && season != null && crop.isNotEmpty(),
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
        "HARVEST_READY" -> FarmColors.InfoBg to FarmColors.Info
        "ACTIVE" -> FarmColors.PrimaryLight to FarmColors.Primary
        else -> FarmColors.WarningBg to FarmColors.Warning
    }
    Pill(status.replace('_', ' '), bg, fg)
}

@Composable
fun MyReservationsList(reservations: List<ReservationItem>, onSelect: (ReservationItem) -> Unit, onExplore: () -> Unit) {
    if (reservations.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Landscape, null, tint = FarmColors.TextMuted, modifier = Modifier.size(44.dp))
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
            FarmCard(Modifier.fillMaxWidth(), onClick = { onSelect(res) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        FText(res.cropName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                        listOf(res.plotTitle, res.seasonName).filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let {
                            IconLabel(Icons.Rounded.Agriculture, it.joinToString(" · "), fontSize = 12)
                        }
                    }
                    reservationStatusPill(res.status)
                }
                if (isClosed(res.status)) {
                    FText(nextStepText(res.status, res.expiresAt), variant = TextVariant.Caption, color = FarmColors.TextBody, modifier = Modifier.padding(top = 8.dp))
                } else {
                    StatusRail(reservationStage(res.status), nextStepText(res.status, res.expiresAt), Modifier.padding(top = 10.dp), total = RESERVATION_STAGES.size)
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { FText("Target Harvest", variant = TextVariant.Caption); FText("${qtyText(res.expectedQtyKg)} kg", variant = TextVariant.Label) }
                    if (res.deposit > 0) Column(Modifier.weight(1f)) { FText("Deposit", variant = TextVariant.Caption); FText(formatRupees(res.deposit), variant = TextVariant.Label) }
                    Pill("Open →", FarmColors.PrimaryLight, FarmColors.Primary, fontSize = 12)
                }
            }
        }
    }
}

