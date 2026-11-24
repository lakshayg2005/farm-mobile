package com.farm.customer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmColors
import com.farm.core.ui.FarmInput
import com.farm.core.ui.IconLabel
import com.farm.core.ui.TextVariant
import com.farm.customer.land.LandFilterState
import com.farm.customer.land.LandSortOption
import com.farm.customer.land.SEASON_NAMES
import com.farm.customer.land.cropTitle

/** The radii offered for the land search (the server accepts 1 to 200 km). */
private val LAND_RADII = listOf(5, 10, 25, 50, 100, 200)

private val LandSortOption.label: String
    get() = when (this) {
        LandSortOption.DISTANCE -> "Nearest"
        LandSortOption.PRICE_ASC -> "Cheapest"
        LandSortOption.PRICE_DESC -> "Priciest"
        LandSortOption.ACRES_DESC -> "Largest"
        LandSortOption.ACRES_ASC -> "Smallest"
    }

private fun numberText(v: Double?): String = v?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""

/**
 * Filters for reserving land: how far to search, season, crop, plot size, price per kg and sort.
 * Edits a draft; nothing changes until "Show plots". Radius, season and crop search the server
 * again; the rest filter what was found.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandFilterSheet(
    filters: LandFilterState,
    crops: List<String>,
    onApply: (LandFilterState) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    var minAcres by remember { mutableStateOf(numberText(filters.minAcres)) }
    var maxAcres by remember { mutableStateOf(numberText(filters.maxAcres)) }
    var maxPrice by remember { mutableStateOf(numberText(filters.maxPricePerKg)) }
    // Keep the chosen crop on offer even if the current results no longer include it.
    val cropChoices = (crops + listOfNotNull(filters.crop?.lowercase())).distinct().sorted()

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            FText("Filter plots", variant = TextVariant.H2)

            FilterSection(Icons.Rounded.TrackChanges, "Search radius") {
                LAND_RADII.forEach { r -> Chip("Within $r km", draft.radiusKm == r, { draft = draft.copy(radiusKm = r) }) }
            }
            FilterSection(Icons.Rounded.Event, "Season") {
                Chip("Any season", draft.season == null, { draft = draft.copy(season = null) })
                SEASON_NAMES.forEach { s -> Chip(s, draft.season == s, { draft = draft.copy(season = s) }) }
            }
            if (cropChoices.isNotEmpty()) {
                FilterSection(Icons.Rounded.Eco, "Crop you want to grow") {
                    Chip("Any crop", draft.crop == null, { draft = draft.copy(crop = null) })
                    cropChoices.forEach { c -> Chip(cropTitle(c), draft.crop.equals(c, true), { draft = draft.copy(crop = c) }) }
                }
            }

            SectionTitle(Icons.Rounded.SquareFoot, "Plot size (acres)")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FarmInput(minAcres, { minAcres = it }, Modifier.weight(1f), placeholder = "Min", keyboardType = KeyboardType.Decimal)
                Text("  to  ", color = FarmColors.TextMuted)
                FarmInput(maxAcres, { maxAcres = it }, Modifier.weight(1f), placeholder = "Max", keyboardType = KeyboardType.Decimal)
            }

            SectionTitle(Icons.Rounded.CurrencyRupee, "Highest price (₹ per kg)")
            FarmInput(maxPrice, { maxPrice = it }, placeholder = "Any price", keyboardType = KeyboardType.Decimal)

            FilterSection(Icons.Rounded.Sort, "Sort by") {
                LandSortOption.entries.forEach { o -> Chip(o.label, draft.sort == o, { draft = draft.copy(sort = o) }) }
            }

            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FarmButton("Reset", { onReset(); onClose() }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                FarmButton("Show plots", {
                    onApply(
                        draft.copy(
                            minAcres = minAcres.toDoubleOrNull(),
                            maxAcres = maxAcres.toDoubleOrNull(),
                            maxPricePerKg = maxPrice.toDoubleOrNull(),
                        ),
                    )
                    onClose()
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    IconLabel(
        icon, title, Modifier.padding(top = 18.dp, bottom = 8.dp),
        color = FarmColors.TextDark, iconColor = FarmColors.Primary, fontSize = 14, fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    SectionTitle(icon, title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
