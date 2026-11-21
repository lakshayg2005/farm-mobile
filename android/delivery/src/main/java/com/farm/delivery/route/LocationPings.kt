package com.farm.delivery.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.farm.core.api.LogisticsApi
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val PING_INTERVAL_MS = 30_000L

class LocationPingState {
    var isTracking by mutableStateOf(false)
    var lastPingTime by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var pingCount by mutableIntStateOf(0)
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Pings the partner's GPS position to logistics every 30 s while [active] (the route still has
 * stops to work) and the screen is shown.
 */
@Composable
fun rememberLocationPings(active: Boolean): LocationPingState {
    val context = LocalContext.current
    val state = remember { LocationPingState() }
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
        if (!granted) {
            state.error = "Location permission denied. Please allow location to track your route."
            state.isTracking = false
        }
    }

    LaunchedEffect(active, granted) {
        if (!active) {
            state.isTracking = false
            return@LaunchedEffect
        }
        if (!granted) {
            if (!asked) {
                asked = true
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            return@LaunchedEffect
        }
        state.isTracking = true
        state.error = null
        val client = LocationServices.getFusedLocationProviderClient(context)
        try {
            while (true) {
                doPing(client, state)
                delay(PING_INTERVAL_MS)
            }
        } finally {
            state.isTracking = false
        }
    }
    return state
}

@SuppressLint("MissingPermission") // checked by the caller before the loop starts
private suspend fun doPing(client: com.google.android.gms.location.FusedLocationProviderClient, state: LocationPingState) {
    try {
        val token = CancellationTokenSource()
        val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
            ?: client.lastLocation.await()
            ?: throw IllegalStateException("Location unavailable. Is GPS turned on?")
        LogisticsApi.sendLocationPing(
            lat = loc.latitude,
            lng = loc.longitude,
            speed = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            heading = if (loc.hasBearing()) loc.bearing.toDouble() else null,
            accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
            recordedAt = Instant.ofEpochMilli(loc.time).toString(),
        )
        state.lastPingTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
        state.pingCount += 1
        state.error = null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        state.error = e.message ?: "Failed to get location"
    }
}
