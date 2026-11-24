package com.farm.delivery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping

import com.farm.delivery.push.startPush

import com.farm.delivery.push.routeForPush

import com.farm.delivery.push.pushAvailable

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.MutableState

import androidx.compose.runtime.LaunchedEffect

import android.os.Build

import android.content.pm.PackageManager

import android.content.Intent

import android.Manifest

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
        startPush(this)
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
        setContent { FarmTheme { DeliveryNav(deepLink) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = deepLinkOf(intent)
    }

    /** The route a tapped push asks for, consumed once. */
    private val deepLink = mutableStateOf<String?>(null)

    private fun deepLinkOf(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        val data = extras.keySet().mapNotNull { k -> extras.getString(k)?.let { k to it } }.toMap()
        return routeForPush(data)
    }
}

@Composable
private fun DeliveryNav(deepLink: MutableState<String?>) {
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
            DeliveryHomeScreen(
                onLogin = { nav.navigate("login") },
                onTeam = { nav.navigate("team") },
                onRoute = { date -> nav.navigate(if (date == null) "route" else "route?date=$date") },
            )
        }
        composable("login") {
            SharedLoginScreen(
                appName = "Farm Delivery",
                appSubtitle = "Pooled routes & contactless farm dispatch",
                requiredRole = "delivery",
                logoIcon = Icons.Rounded.LocalShipping,
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
