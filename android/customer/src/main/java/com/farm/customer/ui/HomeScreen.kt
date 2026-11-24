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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farm.core.api.loadErrorMessage
import com.farm.core.auth.AuthManager
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.ErrorState
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmColors
import com.farm.core.ui.IconLabel
import com.farm.core.ui.LoadingSkeleton
import com.farm.core.ui.ProfileScreen
import com.farm.core.ui.PullRefresh
import com.farm.core.ui.TextVariant
import com.farm.core.ui.rememberRefreshTick
import com.farm.customer.browse.NEAR_ME
import com.farm.customer.browse.NearMeResult
import com.farm.customer.browse.POPULAR_DISTRICTS
import com.farm.customer.browse.clampRadius
import com.farm.customer.browse.rememberNearMe
import com.farm.customer.checkout.CartStore
import com.farm.customer.land.LandFilterState
import com.farm.customer.land.SEASON_NAMES
import com.farm.customer.land.activeLandFilters
import com.farm.customer.land.cropsOf
import com.farm.customer.prefs.LocalLists

// The land service names its seasons Kharif, Rabi and Zaid; the chip filters on the server.
private val SEASONS = listOf("ALL" to "All Seasons") + SEASON_NAMES.map { it to it }

class HomeNav(
    val cart: () -> Unit,
    val order: (String) -> Unit,
    val notifications: () -> Unit,
    val team: () -> Unit,
    val orders: () -> Unit,
    val login: () -> Unit,
    val listing: (String) -> Unit,
    /** Opens one reservation from the server (deposit, status, crop timeline). */
    val reservation: (String) -> Unit,
)

@Composable
fun HomeScreen(nav: HomeNav, vm: HomeViewModel = viewModel()) {
    val auth by AuthManager.state.collectAsState()
    val cart by CartStore.cart.collectAsState()
    var filterOpen by remember { mutableStateOf(false) }
    var landFilterOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var nearMeNote by remember { mutableStateOf<String?>(null) }
    // Search around the phone's own position: once on open (if allowed), or from the place picker.
    val findMe = rememberNearMe(auto = vm.location.name != NEAR_ME) { result ->
        when (result) {
            is NearMeResult.Found -> { vm.changeLocation(result.place); nearMeNote = null }
            NearMeResult.Denied -> nearMeNote = "Location is off for this app, so we search around ${vm.location.name}. Pick a place above or allow location."
            NearMeResult.Unavailable -> nearMeNote = "Could not find your position (is GPS on?). Searching around ${vm.location.name}."
        }
    }
    // A push (e.g. a reservation was accepted) or coming back to the app re-reads the land tab.
    val tick = rememberRefreshTick()
    LaunchedEffect(tick) { if (tick > 0 && vm.tab == HomeTab.LAND) vm.refreshLand() }
    val cartCount = cart.items.sumOf { it.qty }.toInt()

    Scaffold(
        containerColor = FarmColors.Background,
        bottomBar = { CustomerBottomBar(vm.tab) { vm.tab = it } },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (vm.tab) {
                HomeTab.PRODUCE, HomeTab.LAND -> {
                    // Top bar: serif title, the place being searched (tap to change), cart and notifications.
                    Row(
                        Modifier.fillMaxWidth().background(FarmColors.Surface).statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            FText(if (vm.tab == HomeTab.PRODUCE) "Produce" else "Land", variant = TextVariant.H1)
                            Row(
                                Modifier.heightIn(min = 32.dp).clickable { pickerOpen = true }
                                    .semantics { contentDescription = "Location ${vm.location.name}, tap to change" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = FarmColors.Primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                FText(
                                    (if (vm.tab == HomeTab.PRODUCE) "Fresh from farms " else "Farm plots to reserve ") +
                                        (if (vm.location.name == NEAR_ME) "near you" else "near ${vm.location.name}"),
                                    variant = TextVariant.Caption,
                                )
                                Icon(Icons.Rounded.ExpandMore, null, tint = FarmColors.Primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Box(
                            Modifier.size(48.dp).clickable(onClick = nav.cart)
                                .semantics { contentDescription = if (cartCount > 0) "Cart, $cartCount items" else "Cart" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.ShoppingCart, null, tint = FarmColors.TextDark)
                            if (cartCount > 0) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).clip(CircleShape).background(FarmColors.Primary)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) { Text(if (cartCount > 99) "99+" else "$cartCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        if (auth.isAuthenticated) NotificationBell(nav.notifications, background = FarmColors.Surface)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(FarmColors.Border))
                    nearMeNote?.let { FText(it, variant = TextVariant.Caption, modifier = Modifier.fillMaxWidth().background(FarmColors.WarningBg).padding(horizontal = 16.dp, vertical = 8.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (vm.tab == HomeTab.PRODUCE) ProduceTab(vm, nav, onFilter = { filterOpen = true })
                        else LandTab(vm, nav, onFilter = { landFilterOpen = true })
                    }
                }
                HomeTab.ORDERS -> OrdersScreen(
                    onBack = null, onHome = { vm.tab = HomeTab.PRODUCE }, onOrder = nav.order, onNotifications = nav.notifications,
                )
                HomeTab.PROFILE -> ProfileScreen("Farm customer", onLogin = nav.login, onTeam = nav.team)
            }
        }
    }

    if (filterOpen) {
        FilterSheet(vm.filters, onApply = vm::updateFilters, onReset = vm::resetFilters, onClose = { filterOpen = false })
    }
    if (landFilterOpen) {
        LandFilterSheet(
            vm.landFilters, cropsOf(vm.allLoadedPlots),
            onApply = vm::updateLandFilters,
            onReset = { vm.updateLandFilters(LandFilterState()) },
            onClose = { landFilterOpen = false },
        )
    }
    vm.reservingPlot?.let { plot ->
        ReservationSheet(
            plot, onConfirm = { s, c, q -> vm.createReservation(plot, s, c, q) },
            onClose = { vm.reservingPlot = null; vm.reservingSeasonId = null }, initialSeasonId = vm.reservingSeasonId,
        )
    }
    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Select Location") },
            text = {
                Column {
                    FText("Search around your position or a district:", variant = TextVariant.Caption, modifier = Modifier.padding(bottom = 10.dp))
                    PlaceRow(Icons.Rounded.MyLocation, "Use my location", vm.location.name == NEAR_ME, FarmColors.Primary) {
                        pickerOpen = false
                        findMe()
                    }
                    POPULAR_DISTRICTS.forEach { d ->
                        PlaceRow(Icons.Rounded.Place, d.name, vm.location.name == d.name, FarmColors.TextDark) {
                            vm.changeLocation(d)
                            pickerOpen = false
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
private fun PlaceRow(icon: ImageVector, name: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) FarmColors.PrimaryLight else Color.Transparent)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconLabel(icon, name, Modifier.weight(1f), color = color, iconColor = FarmColors.Primary, fontSize = 15, fontWeight = FontWeight.SemiBold)
        if (selected) Icon(Icons.Rounded.Check, null, tint = FarmColors.Primary)
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier, onSearch: (String) -> Unit = {}) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, FarmColors.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = FarmColors.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = FarmColors.TextMuted, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, "Clear search", tint = FarmColors.TextMuted,
                modifier = Modifier.clickable { onChange("") }.padding(start = 6.dp).size(18.dp),
            )
        }
    }
}

/** A compact outlined action next to the search box; [badge] shows how many filters are on. */
@Composable
private fun SmallAction(icon: ImageVector, text: String, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (badge > 0) FarmColors.PrimaryLight else Color.White)
            .border(1.dp, if (badge > 0) FarmColors.Primary else FarmColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = FarmColors.Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
        if (badge > 0) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(18.dp).clip(CircleShape).background(FarmColors.Primary), contentAlignment = Alignment.Center) {
                Text("$badge", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun CenterState(icon: ImageVector, title: String, subtitle: String?, action: String?, onAction: () -> Unit, secondary: Boolean = false) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = FarmColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp).size(44.dp))
        FText(title, variant = TextVariant.H2, textAlign = TextAlign.Center)
        subtitle?.let { FText(it, variant = TextVariant.Caption, textAlign = TextAlign.Center) }
        if (action != null) FarmButton(action, onAction, Modifier.padding(top = 12.dp), variant = if (secondary) ButtonVariant.Secondary else ButtonVariant.Primary)
    }
}

@Composable
private fun ProduceTab(vm: HomeViewModel, nav: HomeNav, onFilter: () -> Unit) {
    val favourites by LocalLists.favourites.collectAsState()
    val recent by LocalLists.recentSearches.collectAsState()
    var suggestOpen by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(
                vm.filters.query, { suggestOpen = true; vm.updateFilters(vm.filters.copy(query = it)) }, "Search produce, crop, or farm...", Modifier.weight(1f),
                onSearch = { suggestOpen = false; LocalLists.addSearch(it) },
            )
            SmallAction(Icons.Rounded.Tune, "Filter", onClick = onFilter)
            SmallAction(if (vm.produceMap) Icons.Rounded.ViewAgenda else Icons.Rounded.Map, if (vm.produceMap) "List" else "Map") { vm.produceMap = !vm.produceMap }
        }
        // Google-style completions under the box: "apl" -> Apple. Tapping one fills the box.
        val suggestions = vm.suggestions
        if (suggestOpen && suggestions.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.White).border(1.dp, FarmColors.Border, RoundedCornerShape(10.dp)),
            ) {
                suggestions.forEach { word ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            suggestOpen = false
                            vm.updateFilters(vm.filters.copy(query = word))
                            LocalLists.addSearch(word)
                        }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = FarmColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(word, fontSize = 14.sp, color = FarmColors.TextMuted)
                    }
                }
            }
        }
        ChipRow {
            Chip("All", vm.filters.crop == null, { vm.toggleCrop(null) }, icon = Icons.Rounded.Eco)
            if (favourites.isNotEmpty()) {
                Chip(
                    "Favourites (${favourites.size})", vm.filters.favouritesOnly,
                    { vm.updateFilters(vm.filters.copy(favouritesOnly = !vm.filters.favouritesOnly)) }, icon = Icons.Rounded.Favorite,
                )
            }
            vm.crops.forEach { crop ->
                Chip(crop.name.ifBlank { crop.id }, vm.filters.crop == crop.id, { vm.toggleCrop(crop.id) })
            }
        }
        if (vm.filters.query.isEmpty() && recent.isNotEmpty()) {
            ChipRow {
                Text("Recent:", fontSize = 12.sp, color = FarmColors.TextMuted, modifier = Modifier.padding(top = 7.dp))
                recent.forEach { q -> Chip(q, false, { vm.updateFilters(vm.filters.copy(query = q)) }, icon = Icons.Rounded.History) }
                Chip("Clear", false, LocalLists::clearSearches)
            }
        }
        vm.produceToast?.let {
            Banner(it, FarmColors.PrimaryLight, FarmColors.PrimaryDark, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), icon = Icons.Rounded.Check) {
                Text("View Cart", color = FarmColors.Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = nav.cart))
            }
        }
        val listings = vm.listings
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("${listings.size}${if (vm.produceNextCursor != null) "+" else ""} ${if (listings.size == 1) "listing" else "listings"} within ${clampRadius(vm.filters.radiusKm)} km", variant = TextVariant.Caption, modifier = Modifier.weight(1f))
            IconLabel(
                Icons.Rounded.Refresh, "Refresh", Modifier.clickable { vm.refreshProduce() }.padding(4.dp),
                color = FarmColors.Primary, fontSize = 12, fontWeight = FontWeight.SemiBold,
            )
        }
        when {
            vm.produceLoading -> LoadingSkeleton()
            vm.produceError != null -> ErrorState(vm.produceError, vm::refreshProduce, title = "Could not load produce")
            vm.produceMap -> ProduceMapView(listings, vm.location, onOpen = { nav.listing(it.id) }, onAdded = vm::onAddedToCart)
            listings.isEmpty() -> PullRefresh(false, vm::refreshProduce, Modifier.fillMaxSize()) {
                CenterState(Icons.Rounded.SearchOff, "No produce found", "Nothing within ${vm.filters.radiusKm} km matches. Widen the radius or reset the filters.", "Reset Filters", vm::resetFilters, secondary = true)
            }
            else -> PullRefresh(false, vm::refreshProduce, Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(listings, key = { it.id }) { item ->
                        ListingCard(item, onOpen = { LocalLists.addSearch(vm.filters.query); nav.listing(item.id) }, onAdded = vm::onAddedToCart)
                    }
                    if (vm.produceNextCursor != null) {
                        item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                vm.produceMoreError?.let { FText(loadErrorMessage(it), variant = TextVariant.Caption, modifier = Modifier.padding(bottom = 6.dp)) }
                                FarmButton(
                                    if (vm.loadingMoreProduce) "Loading…" else if (vm.produceMoreError != null) "Retry" else "Load more",
                                    vm::loadMoreProduce,
                                    variant = ButtonVariant.Secondary,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

@Composable
private fun LandTab(vm: HomeViewModel, nav: HomeNav, onFilter: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(FarmColors.Background).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubTab(Icons.Rounded.Map, "Explore Plots (${vm.plots.size})", vm.landSubTab == LandSubTab.EXPLORE, Modifier.weight(1f)) {
                vm.landSubTab = LandSubTab.EXPLORE
                vm.detailPlot = null
            }
            SubTab(Icons.Rounded.Eco, "My Reservations (${vm.reservations.size})", vm.landSubTab == LandSubTab.MY_RESERVATIONS, Modifier.weight(1f)) {
                vm.landSubTab = LandSubTab.MY_RESERVATIONS
            }
        }
        vm.landToast?.let { Banner(it, FarmColors.PrimaryLight, FarmColors.PrimaryDark, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), icon = Icons.Rounded.Check) }

        val detail = vm.detailPlot
        if (vm.landSubTab == LandSubTab.EXPLORE && detail != null) {
            PlotDetailView(
                detail,
                onReserve = { season -> vm.reservingSeasonId = season.id; vm.reservingPlot = detail },
                onClose = { vm.detailPlot = null },
            )
        } else if (vm.landSubTab == LandSubTab.EXPLORE) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchBar(vm.landFilters.query, { vm.updateLandFilters(vm.landFilters.copy(query = it)) }, "Search plot, village or crop...", Modifier.weight(1f))
                SmallAction(Icons.Rounded.Tune, "Filters", badge = activeLandFilters(vm.landFilters), onClick = onFilter)
                SmallAction(if (vm.landMap) Icons.Rounded.ViewAgenda else Icons.Rounded.Map, if (vm.landMap) "List" else "Map") { vm.landMap = !vm.landMap }
            }
            ChipRow {
                SEASONS.forEach { (id, label) ->
                    Chip(label, (id == "ALL" && vm.landFilters.season == null) || vm.landFilters.season == id, {
                        vm.updateLandFilters(vm.landFilters.copy(season = if (id == "ALL") null else id))
                    })
                }
            }
            val plots = vm.plots
            if (!vm.landLoading && vm.landError == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    FText(
                        if (vm.searchedArea) "${plots.size} ${if (plots.size == 1) "plot" else "plots"} in the map area"
                        else "${plots.size} ${if (plots.size == 1) "plot" else "plots"} within ${vm.landFilters.radiusKm} km",
                        variant = TextVariant.Caption, modifier = Modifier.weight(1f),
                    )
                    IconLabel(
                        Icons.Rounded.Refresh, "Refresh", Modifier.clickable { vm.refreshLand() }.padding(4.dp),
                        color = FarmColors.Primary, fontSize = 12, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            when {
                vm.landLoading -> LoadingSkeleton()
                vm.landError != null -> ErrorState(vm.landError, vm::refreshLand, title = "Could not load plots")
                // the map stays visible when empty, so "Search this area" can still be used
                vm.landMap -> PlotMapView(plots, vm.location, onOpen = { vm.detailPlot = it }, onSearchArea = vm::searchMapArea)
                plots.isEmpty() -> PullRefresh(false, vm::refreshLand, Modifier.fillMaxSize()) {
                    CenterState(
                        Icons.Rounded.Landscape, "No open plots found",
                        "Nothing open within ${vm.landFilters.radiusKm} km for these filters. Widen the radius in Filters, or use the map to search another area.",
                        "Reset Filters", { vm.updateLandFilters(LandFilterState()) }, secondary = true,
                    )
                }
                else -> PullRefresh(false, vm::refreshLand, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(plots, key = { it.id }) { p -> PlotCard(p, onReserve = { vm.detailPlot = it }) }
                        if (vm.landNextOffset != null) item {
                            FarmButton(
                                if (vm.loadingMorePlots) "Loading…" else "Load more plots", vm::loadMorePlots,
                                Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !vm.loadingMorePlots,
                            )
                        }
                        vm.landMoreError?.let { e -> item { FText(loadErrorMessage(e), variant = TextVariant.Caption, color = FarmColors.Error) } }
                        item { Spacer(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        } else {
            when {
                vm.landLoading -> LoadingSkeleton()
                vm.reservationsError != null -> ErrorState(vm.reservationsError, vm::refreshLand, title = "Could not load your reservations")
                else -> PullRefresh(false, vm::refreshLand, Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        MyReservationsList(vm.reservations, onSelect = { nav.reservation(it.id) }, onExplore = { vm.landSubTab = LandSubTab.EXPLORE })
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTab(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) FarmColors.Primary else FarmColors.TextMuted
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, FarmColors.Border, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = color)
    }
}

@Composable
private fun CustomerBottomBar(current: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar(containerColor = FarmColors.Surface, tonalElevation = 0.dp) {
        listOf(
            HomeTab.PRODUCE to (Icons.Rounded.ShoppingBasket to "Produce"),
            HomeTab.LAND to (Icons.Rounded.Landscape to "Land"),
            HomeTab.ORDERS to (Icons.Rounded.ReceiptLong to "Orders"),
            HomeTab.PROFILE to (Icons.Rounded.Person to "Profile"),
        ).forEach { (tab, item) ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(item.first, contentDescription = null) },
                label = { Text(item.second, fontSize = 12.sp, fontWeight = if (current == tab) FontWeight.SemiBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FarmColors.Primary, unselectedIconColor = FarmColors.TextMuted,
                    selectedTextColor = FarmColors.Primary, unselectedTextColor = FarmColors.TextMuted,
                    indicatorColor = FarmColors.PrimaryLight,
                ),
            )
        }
    }
}
