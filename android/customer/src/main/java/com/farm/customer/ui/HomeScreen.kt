package com.farm.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farm.core.auth.AuthManager
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmColors
import com.farm.core.ui.TextVariant
import com.farm.customer.browse.POPULAR_DISTRICTS
import com.farm.customer.checkout.CartStore
import com.farm.customer.land.LandFilterState

private val CROPS = listOf(
    "ALL" to "🌱 All", "tomato" to "🍅 Tomato", "spinach" to "🥬 Spinach", "onion" to "🧅 Onion",
    "potato" to "🥔 Potato", "okra" to "🌿 Okra", "carrot" to "🥕 Carrot", "mango" to "🥭 Mango",
)
private val SEASONS = listOf("ALL" to "All Seasons", "Rabi 2026-27" to "Rabi 2026-27", "Kharif 2026" to "Kharif 2026", "Zaid 2027" to "Zaid 2027")

class HomeNav(
    val cart: () -> Unit,
    val notifications: () -> Unit,
    val team: () -> Unit,
    val orders: () -> Unit,
    val login: () -> Unit,
    val listing: (String) -> Unit,
    val harvest: (String) -> Unit,
)

@Composable
fun HomeScreen(nav: HomeNav, vm: HomeViewModel = viewModel()) {
    val auth by AuthManager.state.collectAsState()
    val cart by CartStore.cart.collectAsState()
    var filterOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val cartCount = cart.items.sumOf { it.qty }.toInt()

    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        // Header
        Row(
            Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (vm.tab == HomeTab.PRODUCE) "🥕 Farm Fresh" else "🌾 Farm Land", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FarmColors.PrimaryDark)
                Row(Modifier.clickable { pickerOpen = true }.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📍 ", fontSize = 12.sp)
                    Text(vm.location.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF40916C))
                    Text(" ▼", fontSize = 10.sp, color = Color(0xFF888888))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    RoundIcon("🛒", Color(0xFFF0FDF4), nav.cart, Color(0xFFBBF7D0))
                    if (cartCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clip(CircleShape).background(FarmColors.Primary)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) { Text(if (cartCount > 99) "99+" else "$cartCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
                if (auth.isAuthenticated) RoundIcon("🔔", FarmColors.Background, nav.notifications)
                RoundIcon("👥", FarmColors.Background, nav.team)
                RoundIcon(if (auth.isAuthenticated) "👤" else "🔑", FarmColors.Background, { if (auth.isAuthenticated) nav.orders() else nav.login() })
            }
        }

        // Main tabs
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp)) {
            TabItem("🥕 Produce (Browse & Buy)", vm.tab == HomeTab.PRODUCE, Modifier.weight(1f)) { vm.tab = HomeTab.PRODUCE }
            TabItem("🌾 Land (Plots & Crops)", vm.tab == HomeTab.LAND, Modifier.weight(1f)) { vm.tab = HomeTab.LAND }
        }

        Box(Modifier.weight(1f)) {
            if (vm.tab == HomeTab.PRODUCE) ProduceTab(vm, nav, onFilter = { filterOpen = true }) else LandTab(vm, nav)
        }
    }

    if (filterOpen) {
        FilterSheet(vm.filters, onApply = vm::updateFilters, onReset = vm::resetFilters, onClose = { filterOpen = false })
    }
    vm.reservingPlot?.let { plot ->
        ReservationSheet(plot, onConfirm = { s, c, q -> vm.createReservation(plot, s, c, q) }, onClose = { vm.reservingPlot = null })
    }
    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Select Location") },
            text = {
                Column {
                    FText("Choose a district to explore nearby produce & plots:", variant = TextVariant.Caption, modifier = Modifier.padding(bottom = 10.dp))
                    POPULAR_DISTRICTS.forEach { d ->
                        val sel = vm.location.name == d.name
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (sel) FarmColors.PrimaryLight else Color.Transparent)
                                .clickable { vm.changeLocation(d); pickerOpen = false }.padding(12.dp),
                        ) {
                            Text("📍 ${d.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (sel) Text("✓", color = FarmColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { FarmButton("Close", { pickerOpen = false }, variant = ButtonVariant.Ghost) },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun RoundIcon(icon: String, bg: Color, onClick: () -> Unit, border: Color? = null) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 17.sp) }
}

@Composable
private fun TabItem(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) FarmColors.Primary else FarmColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(if (active) FarmColors.Primary else Color.Transparent))
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔍", fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = Color(0xFF999999), maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Text("✕", color = Color(0xFF888888), modifier = Modifier.clickable { onChange("") }.padding(start = 6.dp))
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary) }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun CenterState(emoji: String, title: String, subtitle: String?, action: String?, onAction: () -> Unit, secondary: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 40.sp)
        FText(title, variant = TextVariant.H2, textAlign = TextAlign.Center)
        subtitle?.let { FText(it, variant = TextVariant.Caption, textAlign = TextAlign.Center) }
        if (action != null) FarmButton(action, onAction, Modifier.padding(top = 12.dp), variant = if (secondary) ButtonVariant.Secondary else ButtonVariant.Primary)
    }
}

@Composable
private fun LoadingState(text: String, color: Color = FarmColors.Primary) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = color)
        FText(text, variant = TextVariant.Caption, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ProduceTab(vm: HomeViewModel, nav: HomeNav, onFilter: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(vm.filters.query, { vm.updateFilters(vm.filters.copy(query = it)) }, "Search produce, crop, or farm...", Modifier.weight(1f))
            SmallAction("⚙ Filter", onFilter)
            SmallAction(if (vm.produceMap) "📋 List" else "🗺 Map") { vm.produceMap = !vm.produceMap }
        }
        ChipRow {
            CROPS.forEach { (id, label) ->
                Chip(label, (id == "ALL" && vm.filters.crop == null) || vm.filters.crop == id, { vm.toggleCrop(if (id == "ALL") null else id) })
            }
        }
        vm.produceToast?.let {
            Banner(it, Color(0xFFE8F5E9), Color(0xFF1B5E20), Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("View Cart →", color = FarmColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = nav.cart))
            }
        }
        val listings = vm.listings
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("${listings.size} ${if (listings.size == 1) "listing" else "listings"} within ${vm.filters.radiusKm} km", variant = TextVariant.Caption, modifier = Modifier.weight(1f))
            Text("🔄 Refresh", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary, modifier = Modifier.clickable { vm.refreshProduce() }.padding(4.dp))
        }
        when {
            vm.produceLoading -> LoadingState("Finding produce near you...")
            vm.produceError != null -> CenterState("⚠️", "Something went wrong", vm.produceError, "Try Again", vm::refreshProduce)
            listings.isEmpty() -> CenterState("🔍", "No produce found", null, "Reset Filters", vm::resetFilters, secondary = true)
            vm.produceMap -> ProduceMapView(listings, vm.location, onOpen = { nav.listing(it.id) }, onAdded = vm::onAddedToCart)
            else -> LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(listings, key = { it.id }) { item -> ListingCard(item, onOpen = { nav.listing(item.id) }, onAdded = vm::onAddedToCart) }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun LandTab(vm: HomeViewModel, nav: HomeNav) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubTab("🗺 Explore Plots (${vm.plots.size})", vm.landSubTab == LandSubTab.EXPLORE, Modifier.weight(1f)) {
                vm.landSubTab = LandSubTab.EXPLORE
                vm.timelineReservation = null
            }
            SubTab("🌱 My Reservations (${vm.reservations.size})", vm.landSubTab == LandSubTab.MY_RESERVATIONS, Modifier.weight(1f)) {
                vm.landSubTab = LandSubTab.MY_RESERVATIONS
            }
        }
        vm.landToast?.let { Banner(it, Color(0xFFE8F5E9), Color(0xFF1B5E20), Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        if (vm.landSubTab == LandSubTab.EXPLORE) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchBar(vm.landFilters.query, { vm.updateLandFilters(vm.landFilters.copy(query = it)) }, "Search plot title, soil, or crops...", Modifier.weight(1f))
                SmallAction(if (vm.landMap) "📋 List" else "🗺 Map") { vm.landMap = !vm.landMap }
            }
            ChipRow {
                SEASONS.forEach { (id, label) ->
                    Chip("🗓 $label", (id == "ALL" && vm.landFilters.season == null) || vm.landFilters.season == id, {
                        vm.updateLandFilters(vm.landFilters.copy(season = if (id == "ALL") null else id))
                    })
                }
            }
            val plots = vm.plots
            when {
                vm.landLoading -> LoadingState("Finding agricultural plots near you...", Color(0xFF0F766E))
                vm.landError != null -> CenterState("⚠️", "Unable to load plots", vm.landError, "Try Again", vm::refreshLand)
                plots.isEmpty() -> CenterState("🌾", "No plots match filters", null, "Reset Filters", { vm.updateLandFilters(LandFilterState()) }, secondary = true)
                vm.landMap -> PlotMapView(plots, vm.location, onReserve = { vm.reservingPlot = it })
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(plots, key = { it.id }) { p -> PlotCard(p, onReserve = { vm.reservingPlot = it }) }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        } else {
            val timeline = vm.timelineReservation
            if (timeline != null) {
                CropTimelineView(timeline, onClose = { vm.timelineReservation = null }, onHarvest = nav.harvest)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    MyReservationsList(vm.reservations, onSelect = { vm.timelineReservation = it }, onExplore = { vm.landSubTab = LandSubTab.EXPLORE })
                }
            }
        }
    }
}

@Composable
private fun SubTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) Color(0xFF0F766E) else FarmColors.TextMuted)
    }
}
