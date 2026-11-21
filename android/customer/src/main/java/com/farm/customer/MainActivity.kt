package com.farm.customer

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
import com.farm.core.auth.FarmCore
import com.farm.core.ui.FarmTheme
import com.farm.core.ui.SharedLoginScreen
import com.farm.core.ui.TeamScreen
import com.farm.customer.browse.BrowseRepository
import com.farm.customer.land.LandRepository
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
import com.farm.customer.ui.TrackingScreen

class FarmCustomerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FarmCore.init(this)
        BrowseRepository.init(this)
        LandRepository.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FarmTheme { CustomerNav() } }
    }
}

private fun NavHostController.home() = popBackStack("home", inclusive = false)

@Composable
private fun CustomerNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                HomeNav(
                    cart = { nav.navigate("cart") },
                    notifications = { nav.navigate("notifications") },
                    team = { nav.navigate("team") },
                    orders = { nav.navigate("orders") },
                    login = { nav.navigate("login") },
                    listing = { nav.navigate("listing/$it") },
                    harvest = { nav.navigate("harvest/$it") },
                ),
            )
        }
        composable("login") {
            SharedLoginScreen(
                appName = "Farm Customer",
                appSubtitle = "Fresh produce & plots directly from farmers",
                requiredRole = "customer",
                logoEmoji = "🥕",
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
            )
        }
        composable("notifications") {
            NotificationsScreen(onBack = { nav.popBackStack() }, onHome = { nav.home() }, onOrder = { nav.navigate("order/$it") })
        }
        composable("tracking/{id}") { e ->
            TrackingScreen(
                e.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOrders = { nav.navigate("orders") { popUpTo("home") } },
            )
        }
        composable("harvest/{reservationId}") { e ->
            HarvestScreen(
                e.arguments?.getString("reservationId").orEmpty(),
                onBack = { nav.popBackStack() },
                onTrack = { nav.navigate("tracking/$it") },
            )
        }
    }
}
