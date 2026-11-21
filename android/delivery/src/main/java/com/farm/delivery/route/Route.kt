package com.farm.delivery.route

import com.farm.core.api.Pool
import com.farm.core.api.Stop
import java.time.LocalDate
import java.util.Locale

enum class StopType { PICKUP, DROP }
enum class StopStatus { PENDING, DONE, SKIPPED }

/** One stop as the route screen and the pickup / delivery sheets show it. */
data class RouteStop(
    val id: String,
    val orderId: String,
    val seq: Int,
    val type: StopType,
    val status: StopStatus,
    val label: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val customerName: String,
    val items: List<String>,
    val otp: String? = null,
)

data class RouteSummary(
    val pickups: Int,
    val drops: Int,
    val done: Int,
    val finished: Boolean,
    val next: RouteStop?,
)

private fun shortOrder(orderId: String) = "#${orderId.take(8)}"

private fun statusOf(s: String): StopStatus = when (s) {
    "DONE" -> StopStatus.DONE
    "SKIPPED" -> StopStatus.SKIPPED
    else -> StopStatus.PENDING // PENDING, and ARRIVED (arrived but not finished: still the stop to work on)
}

private fun trimNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

fun toRouteStop(stop: Stop): RouteStop {
    val type = if (stop.stopType == "PICKUP") StopType.PICKUP else StopType.DROP
    val name = stop.customerName?.trim()?.ifEmpty { null }
    val weight = stop.weightKg?.takeIf { it != 0.0 }?.let { " · ${trimNumber(it)} kg" } ?: ""
    return RouteStop(
        id = stop.id,
        orderId = stop.orderId,
        seq = stop.seq,
        type = type,
        status = statusOf(stop.status),
        label = if (type == StopType.PICKUP) "Farm pickup for order ${shortOrder(stop.orderId)}"
        else "Customer: ${name ?: "order ${shortOrder(stop.orderId)}"}",
        address = stop.address?.trim()?.ifEmpty { null }
            ?: String.format(Locale.US, "%.5f, %.5f", stop.lat, stop.lng),
        lat = stop.lat,
        lng = stop.lng,
        customerName = name ?: if (type == StopType.PICKUP) "Farm" else "Customer",
        items = listOf("Order ${shortOrder(stop.orderId)}$weight"),
        otp = stop.otp?.ifEmpty { null },
    )
}

/** The pool's stops in driving order (the pooling job always puts a pickup before its drop). */
fun routeStops(pool: Pool?): List<RouteStop> =
    pool?.stops.orEmpty().sortedBy { it.seq }.map(::toRouteStop)

fun routeSummary(stops: List<RouteStop>): RouteSummary {
    val worked = stops.count { it.status != StopStatus.PENDING }
    return RouteSummary(
        pickups = stops.count { it.type == StopType.PICKUP },
        drops = stops.count { it.type == StopType.DROP },
        done = stops.count { it.status == StopStatus.DONE },
        finished = stops.isNotEmpty() && worked == stops.size,
        next = stops.firstOrNull { it.status == StopStatus.PENDING },
    )
}

/** Marks one stop done in a loaded pool, so the screen updates before the route is re-read. */
fun markStopDone(pool: Pool?, stopId: String): Pool? =
    pool?.copy(stops = pool.stops.map { if (it.id == stopId) it.copy(status = "DONE") else it })

/** Local calendar date as YYYY-MM-DD (the pool date the partner means by "today"). */
fun localIsoDate(d: LocalDate = LocalDate.now()): String = d.toString()

fun mapsUrl(lat: Double, lng: Double, label: String): String =
    "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng" +
        "&destination_place_id=${java.net.URLEncoder.encode(label, "UTF-8")}&travelmode=driving"
