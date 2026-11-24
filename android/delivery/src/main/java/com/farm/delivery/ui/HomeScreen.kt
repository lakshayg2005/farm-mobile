package com.farm.delivery.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Route
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.LogisticsApi
import com.farm.core.api.Pool
import com.farm.core.api.load
import com.farm.core.auth.AuthManager
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.EmptyState
import com.farm.core.ui.ErrorState
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.IconLabel
import com.farm.core.ui.LoadingSkeleton
import com.farm.core.ui.PinColors
import com.farm.core.ui.ProfileScreen
import com.farm.core.ui.PullRefresh
import com.farm.core.ui.ScreenTitle
import com.farm.core.ui.StatusChip
import com.farm.core.ui.TextVariant
import com.farm.core.ui.rememberRefreshTick
import com.farm.delivery.route.RouteStop
import com.farm.delivery.route.StopStatus
import com.farm.delivery.route.StopType
import com.farm.delivery.route.groupByDay
import com.farm.delivery.route.historyDayLabel
import com.farm.delivery.route.open
import com.farm.delivery.route.routeLengthM
import com.farm.delivery.route.tally
import com.farm.delivery.route.tallyText
import com.farm.delivery.route.toRouteStop
import java.time.LocalDate

/** Days of routes the History tab shows (today included). */
private const val HISTORY_DAYS = 14

@Composable
fun DeliveryHomeScreen(onLogin: () -> Unit, onTeam: () -> Unit, onRoute: (String?) -> Unit) {
    val auth by AuthManager.state.collectAsState()
    if (auth.isLoading) return CenteredLoading()
    if (auth.isAuthenticated && auth.hasRole("delivery")) return PartnerTabs(onLogin, onTeam)

    Column(
        Modifier.fillMaxSize().background(FarmColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FarmCard(Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
            IconLabel(Icons.Rounded.LocalShipping, "Farm Delivery", color = FarmColors.TextDark, iconColor = FarmColors.Primary, fontSize = 24, fontWeight = FontWeight.Bold)
            FText(
                "Welcome! Log in with your Delivery Partner account to access your assigned pooled routes and stops.",
                color = FarmColors.TextLabel, modifier = Modifier.padding(top = 8.dp),
            )
            FarmButton("Log In / Sign Up", onLogin, Modifier.fillMaxWidth().padding(top = 24.dp))
            FarmButton("Meet the Team", onTeam, Modifier.fillMaxWidth().padding(top = 8.dp), variant = ButtonVariant.Ghost, icon = Icons.Rounded.Groups)
        }
    }
}

private enum class PartnerTab { ROUTE, HISTORY, PROFILE }

/** Signed-in partner: bottom bar with today's Route, History and Profile. */
@Composable
private fun PartnerTabs(onLogin: () -> Unit, onTeam: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(PartnerTab.ROUTE) }
    Scaffold(
        containerColor = FarmColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = FarmColors.Surface, tonalElevation = 0.dp) {
                listOf(
                    PartnerTab.ROUTE to (Icons.Rounded.Map to "Route"),
                    PartnerTab.HISTORY to (Icons.Rounded.History to "History"),
                    PartnerTab.PROFILE to (Icons.Rounded.Person to "Profile"),
                ).forEach { (t, item) ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(item.first, contentDescription = null) },
                        label = { Text(item.second, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FarmColors.Primary, unselectedIconColor = FarmColors.TextMuted,
                            selectedTextColor = FarmColors.Primary, unselectedTextColor = FarmColors.TextMuted, indicatorColor = FarmColors.PrimaryLight,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                PartnerTab.ROUTE -> RouteScreen(dateParam = null, onBack = null, onHome = { tab = PartnerTab.ROUTE })
                PartnerTab.HISTORY -> HistoryTab()
                PartnerTab.PROFILE -> ProfileScreen("Farm delivery partner", onLogin = onLogin, onTeam = onTeam)
            }
        }
    }
}

/**
 * Every route of the last [HISTORY_DAYS] days, today first (GET /logistics/my-routes), grouped by
 * day with how each went; a route opens to its stops. Pull down, a push, or coming back reloads.
 */
@Composable
private fun HistoryTab() {
    var routes by remember { mutableStateOf<List<Pool>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    val tick = rememberRefreshTick()
    LaunchedEffect(reload, tick) {
        load { LogisticsApi.myRoutes(HISTORY_DAYS) }
            .onSuccess { routes = it; error = null }
            .onFailure { error = it }
        refreshing = false
    }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle("History", "Your routes from the last two weeks, today included")
        val list = routes
        when {
            list == null && error == null -> LoadingSkeleton()
            list == null -> ErrorState(error, { reload++ }, title = "Could not load your history")
            else -> PullRefresh(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (list.isEmpty()) item {
                        EmptyState(
                            "No routes yet", "Routes you take appear here, today's included. Pull down to check again.",
                            icon = Icons.Rounded.Route,
                        )
                    }
                    groupByDay(list).forEach { (date, pools) ->
                        val day = runCatching { LocalDate.parse(date) }.getOrNull()
                        item(key = "day-$date") {
                            Text(
                                (day?.let { historyDayLabel(it, today) } ?: date).uppercase(),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextLabel, letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(pools, key = { it.id }) { pool ->
                            HistoryRouteCard(pool, open = openId == pool.id) { openId = if (openId == pool.id) null else pool.id }
                        }
                    }
                }
            }
        }
    }
}

private fun poolStatusLabel(status: String): Pair<String, androidx.compose.ui.graphics.Color> = when (status) {
    "COMPLETED" -> "Completed" to FarmColors.Primary
    "IN_PROGRESS" -> "In progress" to FarmColors.Info
    "ASSIGNED" -> "Assigned" to FarmColors.Warning
    "CANCELLED" -> "Cancelled" to FarmColors.Error
    else -> status.lowercase().replaceFirstChar { it.uppercase() } to FarmColors.TextMuted
}

@Composable
private fun HistoryRouteCard(pool: Pool, open: Boolean, onToggle: () -> Unit) {
    val stops = pool.stops.sortedBy { it.seq }.map(::toRouteStop)
    val t = tally(stops)
    val (label, color) = poolStatusLabel(pool.status)
    val km = pool.totalKm ?: (routeLengthM(stops) / 1000)
    FarmCard(Modifier.fillMaxWidth(), onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tallyText(t), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconLabel(Icons.Rounded.Inventory2, "${stops.count { it.type == StopType.PICKUP }}", color = PinColors.Pickup, fontSize = 12)
                    Spacer(Modifier.width(10.dp))
                    IconLabel(Icons.Rounded.Home, "${stops.count { it.type == StopType.DROP }}", color = PinColors.Drop, fontSize = 12)
                    Spacer(Modifier.width(10.dp))
                    FText(String.format(java.util.Locale.US, "%.1f km", km), variant = TextVariant.Caption)
                }
            }
            StatusChip(label, color)
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = FarmColors.TextMuted, modifier = Modifier.padding(start = 4.dp))
        }
        if (open) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stops.forEach { HistoryStopRow(it) }
            }
        }
    }
}

@Composable
private fun HistoryStopRow(stop: RouteStop) {
    val color = when {
        stop.status == StopStatus.SKIPPED -> PinColors.Muted
        stop.type == StopType.PICKUP -> PinColors.Pickup
        else -> PinColors.Drop
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text("${stop.seq}", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stop.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (stop.status == StopStatus.SKIPPED) TextDecoration.LineThrough else null,
            )
            Text(stop.address, fontSize = 12.sp, color = FarmColors.TextLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when (stop.status) {
                StopStatus.DONE -> "Done"
                StopStatus.SKIPPED -> "Skipped"
                StopStatus.ARRIVED -> "At stop"
                StopStatus.PENDING -> "Open"
            },
            fontSize = 12.sp,
            color = if (stop.status.open) FarmColors.Warning else FarmColors.TextLabel,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Spacer(Modifier.height(0.dp))
}
