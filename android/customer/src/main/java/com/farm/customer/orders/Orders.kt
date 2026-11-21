package com.farm.customer.orders

import com.farm.core.api.DeliveryAddress
import com.farm.core.api.OrderEvent
import com.farm.core.api.OrderSummary
import com.farm.core.api.PaymentRecord
import com.farm.core.api.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ─── Orders ─────────────────────────────────────────────────────────────────

data class StatusMeta(val label: String, val color: String, val bg: String, val emoji: String)

val STATUS_META = mapOf(
    "PLACED" to StatusMeta("Order Placed", "#1565c0", "#e3f2fd", "🧾"),
    "CONFIRMED" to StatusMeta("Confirmed", "#6a1b9a", "#f3e5f5", "✅"),
    "PACKED" to StatusMeta("Packed", "#e65100", "#fff3e0", "📦"),
    "QC_PASSED" to StatusMeta("Quality Checked", "#e65100", "#fff3e0", "🔎"),
    "READY_FOR_DISPATCH" to StatusMeta("Ready to Dispatch", "#f9a825", "#fffde7", "🚜"),
    "POOLED" to StatusMeta("Ready to Dispatch", "#f9a825", "#fffde7", "🚜"),
    "IN_TRANSIT" to StatusMeta("Out for Delivery", "#00695c", "#e0f2f1", "🚚"),
    "DELIVERED" to StatusMeta("Delivered", "#2d6a4f", "#e8f5e9", "🎉"),
    "COMPLETED" to StatusMeta("Delivered", "#2d6a4f", "#e8f5e9", "🎉"),
    "CANCELLED" to StatusMeta("Cancelled", "#c62828", "#ffebee", "❌"),
)

fun statusMeta(status: String) = STATUS_META[status] ?: StatusMeta(status.replace('_', ' '), "#475569", "#f1f5f9", "📍")

/** The steps a customer sees. Statuses in between (QC_PASSED, POOLED, COMPLETED) map onto one. */
val TIMELINE_STEPS = listOf("PLACED", "CONFIRMED", "PACKED", "READY_FOR_DISPATCH", "IN_TRANSIT", "DELIVERED")

private val STEP_OF = mapOf(
    "PLACED" to 0, "CONFIRMED" to 1, "PACKED" to 2, "QC_PASSED" to 2, "READY_FOR_DISPATCH" to 3, "POOLED" to 3,
    "IN_TRANSIT" to 4, "DELIVERED" to 5, "COMPLETED" to 5, "CANCELLED" to -1,
)

fun stepIndex(status: String): Int = STEP_OF[status] ?: 0

fun isActive(status: String) = status !in listOf("DELIVERED", "COMPLETED", "CANCELLED")

/** Active orders first, then finished ones; each newest first. */
fun splitOrders(orders: List<OrderSummary>): Pair<List<OrderSummary>, List<OrderSummary>> {
    val newest = orders.sortedByDescending { it.createdAt }
    return newest.filter { isActive(it.status) } to newest.filter { !isActive(it.status) }
}

fun orderTitle(orderType: String?, harvestOption: String?): String = when {
    orderType != "HARVEST" -> "Produce order"
    harvestOption == "BUYBACK" -> "Harvest buy-back"
    else -> "Harvest delivery"
}

fun itemsLabel(count: Int) = if (count == 1) "1 item" else "$count items"
fun shortId(id: String) = "#${id.take(8)}"

fun rupees(amount: Double): String {
    val f = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }
    return "₹" + f.format(amount)
}

fun addressLabel(a: DeliveryAddress?): String? =
    a?.let { listOfNotNull(it.line1, it.line2, it.city, it.pincode).filter(String::isNotEmpty).joinToString(", ") }

/** When the order reached each timeline step (the first event that maps onto it). */
fun stepTimes(events: List<OrderEvent>): List<String?> {
    val times = MutableList<String?>(TIMELINE_STEPS.size) { null }
    events.sortedBy { it.occurredAt }.forEach { e ->
        val i = stepIndex(e.status)
        if (i >= 0 && times[i] == null) times[i] = e.occurredAt
    }
    return times
}

/** Notifications about an order carry its id in the payload; tapping one opens that order. */
fun notificationOrderId(payload: JsonObject?): String? =
    (payload?.get("orderId") as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifEmpty { null }

fun parseInstant(iso: String): Instant? = runCatching { OffsetDateTime.parse(iso).toInstant() }
    .recoverCatching { Instant.parse(iso) }
    .recoverCatching { java.time.LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant() }
    .getOrNull()

fun timeAgo(iso: String, now: Instant = Instant.now()): String {
    val t = parseInstant(iso) ?: return ""
    val mins = maxOf(0, Duration.between(t, now).toMinutes())
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins / 60 < 24 -> "${mins / 60}h ago"
        else -> "${mins / 60 / 24}d ago"
    }
}

private val IN = Locale("en", "IN")

fun formatDate(iso: String): String = runCatching {
    val d = parseInstant(iso)?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.parse(iso.take(10))
    d.format(DateTimeFormatter.ofPattern("d MMM yyyy", IN))
}.getOrDefault(iso)

fun formatDateTime(iso: String): String = parseInstant(iso)?.atZone(ZoneId.systemDefault())
    ?.format(DateTimeFormatter.ofPattern("d MMM, hh:mm a", IN)) ?: iso

// ─── Tracking ───────────────────────────────────────────────────────────────

enum class Phase(val text: String) {
    PREPARING("Your order is being prepared"),
    POOLED("A delivery partner will collect your order"),
    IN_TRANSIT("Your order is on its way"),
    DELIVERED("Delivered"),
    CANCELLED("This order was cancelled"),
}

private val ORDER_PHASES = mapOf(
    "POOLED" to Phase.POOLED, "IN_TRANSIT" to Phase.IN_TRANSIT, "DELIVERED" to Phase.DELIVERED,
    "COMPLETED" to Phase.DELIVERED, "CANCELLED" to Phase.CANCELLED,
)

/** The order service is the truth; logistics can be ahead, so the further one wins (unless cancelled). */
fun phaseOf(orderStatus: String, track: Track?): Phase {
    val fromOrder = ORDER_PHASES[orderStatus] ?: Phase.PREPARING
    if (fromOrder == Phase.CANCELLED) return fromOrder
    val rank = listOf(Phase.PREPARING, Phase.POOLED, Phase.IN_TRANSIT, Phase.DELIVERED)
    val fromTrack = ORDER_PHASES[track?.status ?: ""] ?: Phase.PREPARING
    return if (rank.indexOf(fromTrack) > rank.indexOf(fromOrder)) fromTrack else fromOrder
}

fun canRate(orderStatus: String, rated: Boolean) = orderStatus == "COMPLETED" && !rated
fun shouldPoll(phase: Phase) = phase != Phase.DELIVERED && phase != Phase.CANCELLED

data class LatLng(val lat: Double, val lng: Double)

fun haversineKm(a: LatLng, b: LatLng): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
    return 6371 * 2 * atan2(sqrt(h), sqrt(1 - h))
}

fun distanceLabel(km: Double) = if (km < 1) "${(km * 1000).roundToInt()} m" else String.format(Locale.US, "%.1f km", km)

fun etaLabel(eta: String?, now: Instant): String {
    val t = eta?.let(::parseInstant) ?: return ""
    val minutes = Duration.between(now, t).toMillis() / 60000.0
    val m = minutes.roundToInt()
    return when {
        m <= 0 -> "due now"
        m < 60 -> "in $m min"
        else -> "in ${m / 60} h ${m % 60} min"
    }
}

fun seenLabel(updatedAt: String?, now: Instant): String {
    val t = updatedAt?.let(::parseInstant) ?: return ""
    val seconds = Duration.between(t, now).seconds
    if (seconds < 45) return "just now"
    val minutes = (seconds / 60.0).roundToInt()
    return if (minutes < 60) "$minutes min ago" else "${floor(minutes / 60.0).toInt()} h ago"
}

data class Marker(val key: String, val x: Float, val y: Float)

/** Place points on a width x height canvas keeping proportions; north is up; one point sits in the middle. */
fun project(points: List<Pair<String, LatLng>>, width: Float, height: Float, padding: Float = 28f): List<Marker> {
    if (points.isEmpty()) return emptyList()
    val midLat = points.sumOf { it.second.lat } / points.size
    val kx = cos(Math.toRadians(midLat))
    val xs = points.map { it.second.lng * kx }
    val ys = points.map { it.second.lat }
    val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
    val spanX = maxX - minX; val spanY = maxY - minY
    val innerW = width - 2 * padding; val innerH = height - 2 * padding
    val scale = if (spanX == 0.0 && spanY == 0.0) 0.0 else minOf(
        if (spanX == 0.0) Double.POSITIVE_INFINITY else innerW / spanX,
        if (spanY == 0.0) Double.POSITIVE_INFINITY else innerH / spanY,
    )
    return points.mapIndexed { i, p ->
        Marker(
            p.first,
            (width / 2 + (xs[i] - (minX + maxX) / 2) * scale).toFloat(),
            (height / 2 - (ys[i] - (minY + maxY) / 2) * scale).toFloat(),
        )
    }
}

// ─── Harvest ────────────────────────────────────────────────────────────────

enum class HarvestStage(val text: String) {
    NOT_READY("Your harvest is being checked by an inspector. You can choose here once it passes."),
    CHOOSE("Your harvest passed the quality check. Choose what happens next."),
    DELIVERY("Your harvest is on its way to you."),
    COMPLETED("All done."),
    CANCELLED("This harvest order was cancelled and your deposit refunded."),
}

fun harvestStageOf(status: String?, harvestOption: String?): HarvestStage = when {
    status == null -> HarvestStage.NOT_READY
    status == "CANCELLED" -> HarvestStage.CANCELLED
    status == "COMPLETED" -> HarvestStage.COMPLETED
    harvestOption != null -> HarvestStage.DELIVERY
    else -> HarvestStage.CHOOSE
}

fun shouldPollHarvest(stage: HarvestStage) = stage != HarvestStage.COMPLETED && stage != HarvestStage.CANCELLED

data class HarvestSummary(
    val value: Double, val deliveryFee: Double, val depositPaid: Double,
    val balancePaid: Double, val balanceDue: Double, val credit: Double,
)

fun summarise(subtotal: Double, deliveryFee: Double, balanceDue: Double, payments: List<PaymentRecord>): HarvestSummary {
    fun sum(kind: String) = payments.filter { it.kind == kind && it.status == "SUCCEEDED" }.sumOf { it.amount }
    return HarvestSummary(subtotal, deliveryFee, sum("DEPOSIT"), sum("BALANCE"), balanceDue, sum("REFUND"))
}

/** Delivery dates the customer can pick: tomorrow and the next two days. */
fun deliveryDates(today: LocalDate = LocalDate.now(), count: Int = 3): List<String> =
    (1..count).map { today.plusDays(it.toLong()).toString() }

data class ChoiceError(val message: String, val refresh: Boolean)

private val CHOICE_ERRORS = mapOf(
    "HARVEST_NOT_READY" to ChoiceError("The harvest has not passed the quality check yet.", true),
    "HARVEST_OPTION_CHOSEN" to ChoiceError("You already chose an option for this harvest.", true),
    "HARVEST_ORDER_CLOSED" to ChoiceError("This harvest order is closed.", true),
    "RESERVATION_NOT_FOUND" to ChoiceError("Reservation not found.", false),
    "INVALID_DELIVERY_DATE" to ChoiceError("Pick a delivery date from today up to 30 days ahead.", false),
)

fun parseChoiceError(status: Int, code: String?): ChoiceError = CHOICE_ERRORS[code ?: ""] ?: when (status) {
    401, 403 -> ChoiceError("Log in again to continue.", false)
    422 -> ChoiceError("Check the delivery address and try again.", false)
    else -> ChoiceError("Something went wrong. Try again.", false)
}
