package com.farm.delivery

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.farm.core.auth.FarmCore
import com.farm.core.ui.FarmTheme
import com.farm.core.ui.SharedLoginScreen
import com.farm.core.ui.TeamScreen
import com.farm.delivery.ui.DeliveryHomeScreen
import com.farm.delivery.ui.RouteScreen

class FarmDeliveryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FarmCore.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FarmTheme { DeliveryNav() } }
    }
}

@Composable
private fun DeliveryNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            DeliveryHomeScreen(
                onLogin = { nav.navigate("login") },
                onTeam = { nav.navigate("team") },
                onRoute = { nav.navigate("route") },
            )
        }
        composable("login") {
            SharedLoginScreen(
                appName = "Farm Delivery",
                appSubtitle = "Pooled routes & contactless farm dispatch",
                requiredRole = "delivery",
                logoEmoji = "🚚",
                onSuccess = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("team") { TeamScreen(onBack = { nav.popBackStack() }) }
        composable(
            "route?date={date}",
            arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            RouteScreen(
                dateParam = entry.arguments?.getString("date"),
                onBack = { nav.popBackStack() },
                onHome = { nav.popBackStack("home", inclusive = false) },
            )
        }
    }
}
