package com.farm.customer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco

import com.farm.customer.notifications.startPush

import com.farm.customer.notifications.routeFor

import com.farm.customer.notifications.pushTarget

import com.farm.customer.notifications.pushAvailable

import com.farm.customer.notifications.EXTRA_RESERVATION_ID

import com.farm.customer.notifications.EXTRA_ORDER_ID

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.MutableState

import androidx.compose.runtime.LaunchedEffect

import android.os.Build

import android.content.pm.PackageManager

import android.content.Intent

import android.Manifest

import com.farm.customer.prefs.LocalLists

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.farm.core.auth.AuthManager
import com.farm.core.auth.FarmCore
import com.farm.customer.checkout.CartStore
import com.farm.core.ui.FarmTheme
import com.farm.core.ui.SharedLoginScreen
import com.farm.core.ui.TeamScreen
import com.farm.customer.ui.CartScreen
import com.farm.customer.ui.CheckoutScreen
import com.farm.customer.ui.HarvestScreen
import com.farm.customer.ui.HomeNav
import com.farm.customer.ui.HomeScreen
import com.farm.customer.ui.ListingDetailScreen
import com.farm.customer.ui.NotificationsScreen
import com.farm.customer.ui.OrderDetailScreen
import com.farm.customer.ui.OrderPlacedScreen
import com.farm.customer.ui.OrdersScreen
import com.farm.customer.ui.ReservationScreen
import com.farm.customer.ui.TrackingScreen

class FarmCustomerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FarmCore.init(this)
        LocalLists.init(this)
        startPush(this)
        // Signing out (or a session the server no longer accepts) clears what the app keeps locally.
        AuthManager.onSignOut {
            CartStore.clear()
            CartStore.lastCheckout = null
            LocalLists.clear()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = deepLinkOf(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && pushAvailable(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { FarmTheme { CustomerNav(deepLink) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = deepLinkOf(intent)
    }

    /** The screen a tapped push notification asks for (an order or a reservation), consumed once. */
    private val deepLink = mutableStateOf<String?>(null)

    private fun deepLinkOf(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        val data = listOf(EXTRA_ORDER_ID, EXTRA_RESERVATION_ID).mapNotNull { k -> extras.getString(k)?.let { k to it } }.toMap()
        return pushTarget(data)?.let(::routeFor)
    }
}

private fun NavHostController.home() = popBackStack("home", inclusive = false)

@Composable
private fun CustomerNav(deepLink: MutableState<String?>) {
    val nav = rememberNavController()
    val link = deepLink.value
    LaunchedEffect(link) {
        if (link != null) {
            nav.navigate(link)
            deepLink.value = null
        }
    }
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                HomeNav(
                    cart = { nav.navigate("cart") },
                    order = { nav.navigate("order/$it") },
                    notifications = { nav.navigate("notifications") },
                    team = { nav.navigate("team") },
                    orders = { nav.navigate("orders") },
                    login = { nav.navigate("login") },
                    listing = { nav.navigate("listing/$it") },
                    reservation = { nav.navigate("reservation/$it") },
                ),
            )
        }
        composable("login") {
            SharedLoginScreen(
                appName = "Farm Customer",
                appSubtitle = "Fresh produce & plots directly from farmers",
                requiredRole = "customer",
                logoIcon = Icons.Rounded.Eco,
                onSuccess = { nav.home() },
            )
        }
        composable("team") { TeamScreen(onBack = { nav.popBackStack() }) }
        composable("listing/{id}") { e ->
            ListingDetailScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onCart = { nav.navigate("cart") })
        }
        composable("cart") {
            CartScreen(onBack = { nav.popBackStack() }, onBrowse = { nav.home() }, onCheckout = { nav.navigate("checkout") })
        }
        composable("checkout") {
            CheckoutScreen(
                onBack = { nav.popBackStack() },
                onCart = { nav.popBackStack() },
                onLogin = { nav.navigate("login") },
                onPlaced = { nav.navigate("order-placed") { popUpTo("home") } },
            )
        }
        composable("order-placed") {
            OrderPlacedScreen(
                onTrack = { nav.navigate("tracking/$it") },
                onOrders = { nav.navigate("orders") { popUpTo("home") } },
                onShop = { nav.home() },
            )
        }
        composable("orders") {
            OrdersScreen(
                onBack = { nav.popBackStack() }, onHome = { nav.home() },
                onOrder = { nav.navigate("order/$it") }, onNotifications = { nav.navigate("notifications") },
            )
        }
        composable("order/{id}") { e ->
            OrderDetailScreen(
                e.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() }, onHome = { nav.home() }, onTrack = { nav.navigate("tracking/$it") },
                onCart = { nav.navigate("cart") },
            )
        }
        composable("notifications") {
            NotificationsScreen(
                onBack = { nav.popBackStack() }, onHome = { nav.home() }, onOrder = { nav.navigate("order/$it") },
                onReservation = { nav.navigate("reservation/$it") },
            )
        }
        composable("tracking/{id}") { e ->
            TrackingScreen(
                e.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOrders = { nav.navigate("orders") { popUpTo("home") } },
            )
        }
        composable("reservation/{id}") { e ->
            ReservationScreen(
                e.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onHarvest = { nav.navigate("harvest/$it") },
            )
        }
        composable("harvest/{reservationId}") { e ->
            HarvestScreen(
                e.arguments?.getString("reservationId").orEmpty(),
                onBack = { nav.popBackStack() },
                onTrack = { nav.navigate("tracking/$it") },
                onOrder = { nav.navigate("order/$it") },
            )
        }
    }
}
