package com.farm.customer.browse

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The label shown for the phone's own position. */
const val NEAR_ME = "Near me"

fun nearMe(lat: Double, lng: Double) = LocationCoords(NEAR_ME, lat, lng)

private fun granted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * The phone's current position, or null if none within 10 s. A coarse fix is enough to search
 * nearby; [precise] asks for GPS accuracy (a delivery address).
 */
@SuppressLint("MissingPermission") // callers check the permission first
private suspend fun currentPosition(context: Context, precise: Boolean = false): LocationCoords? = withTimeoutOrNull(if (precise) 15_000 else 10_000) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    val loc = runCatching {
        client.getCurrentLocation(priority, CancellationTokenSource().token).await()
            ?: client.lastLocation.await()
    }.getOrNull()
    loc?.let { nearMe(it.latitude, it.longitude) }
}

/** What happened when the app asked for "near me". */
sealed interface NearMeResult {
    data class Found(val place: LocationCoords) : NearMeResult
    data object Denied : NearMeResult
    data object Unavailable : NearMeResult
}

/**
 * Returns a function that finds the phone's position, asking for location permission when needed,
 * and reports the result. With [auto] it runs once when shown, but only if permission was already
 * granted or has never been asked (so a "no" is not asked again on every launch).
 */
@Composable
fun rememberNearMe(auto: Boolean, precise: Boolean = false, onResult: (NearMeResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf(false) }
    fun locate() = scope.launch { onResult(currentPosition(context, precise)?.let { NearMeResult.Found(it) } ?: NearMeResult.Unavailable) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate() else onResult(NearMeResult.Denied)
    }
    val request: () -> Unit = {
        if (granted(context)) locate() else {
            asked = true
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LaunchedEffect(auto) {
        if (!auto) return@LaunchedEffect
        val prefs = context.getSharedPreferences("near_me", Context.MODE_PRIVATE)
        if (granted(context)) locate()
        else if (!prefs.getBoolean("asked", false) && !asked) {
            prefs.edit().putBoolean("asked", true).apply()
            request()
        }
    }
    return request
}
