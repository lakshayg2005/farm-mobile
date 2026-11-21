package com.farm.customer.checkout

import com.farm.core.api.Checkout
import com.farm.core.api.CheckoutRequest
import com.farm.core.api.DeliveryAddress
import com.farm.core.api.GeoPoint
import com.farm.core.api.Http
import com.farm.core.api.NetworkException
import com.farm.core.api.OrderApi
import com.farm.core.api.OrderItemIn
import com.farm.core.api.PaymentIntent
import com.farm.core.api.PaymentIntentCreate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

// ─── Cart (pure functions on an immutable cart; the cart lives only in the app) ─────────────

data class CartItem(
    val listingId: String,
    val farmId: String,
    val farmName: String,
    val name: String,
    val unit: String,
    val unitPrice: Double,
    val qty: Double,
    /** Stock we last saw for this listing, if known. The server has the final say at checkout. */
    val maxQty: Double? = null,
)

data class Cart(val items: List<CartItem> = emptyList())

/** One entry of `error.details` of a 409 STOCK_UNAVAILABLE. */
data class Shortage(val listingId: String, val requested: Double, val available: Double)

data class FarmGroup(val farmId: String, val farmName: String, val items: List<CartItem>, val subtotal: Double)

fun round2(v: Double): Double = (v * 100 + if (v >= 0) 1e-9 else -1e-9).roundToLong() / 100.0

private fun clamp(qty: Double, maxQty: Double?): Double {
    val rounded = (qty * 1000).roundToLong() / 1000.0
    return if (maxQty != null) minOf(rounded, maxQty) else rounded
}

fun Cart.add(item: CartItem): Cart {
    if (item.qty <= 0) return this
    val existing = items.find { it.listingId == item.listingId }
    if (existing == null) {
        val next = clamp(item.qty, item.maxQty)
        return if (next > 0) Cart(items + item.copy(qty = next)) else this
    }
    return setQty(item.listingId, existing.qty + item.qty)
}

fun Cart.setQty(listingId: String, qty: Double): Cart {
    if (qty <= 0) return remove(listingId)
    return Cart(items.map { if (it.listingId == listingId) it.copy(qty = clamp(qty, it.maxQty)) else it })
}

fun Cart.remove(listingId: String): Cart = Cart(items.filter { it.listingId != listingId })

fun Cart.subtotal(): Double = round2(items.sumOf { round2(it.unitPrice * it.qty) })

/** The checkout creates one order per farm, so the cart is shown grouped the same way. */
fun Cart.groupByFarm(): List<FarmGroup> =
    items.groupBy { it.farmId }.map { (farmId, list) ->
        FarmGroup(farmId, list.first().farmName, list, list.fold(0.0) { acc, i -> round2(acc + round2(i.unitPrice * i.qty)) })
    }

/** Estimated total: items plus one delivery fee per farm. The server computes the real total. */
fun Cart.estimateTotal(feePerFarm: Double): Double = round2(subtotal() + feePerFarm * groupByFarm().size)

/** Cap quantities at what is left after a STOCK_UNAVAILABLE; sold-out lines are removed. */
fun Cart.applyShortages(shortages: List<Shortage>): Cart {
    val available = shortages.associate { it.listingId to it.available }
    return Cart(items.mapNotNull { item ->
        val left = available[item.listingId]
        when {
            left == null -> item
            left > 0 -> item.copy(qty = minOf(item.qty, left), maxQty = left)
            else -> null
        }
    })
}

fun Cart.toOrderItems(): List<OrderItemIn> = items.map { OrderItemIn(it.listingId, it.qty) }

/** Flat delivery fee per farm order that the backend charges (order service setting). */
const val DELIVERY_FEE_PER_ORDER = 20.0

/** In-memory cart shared by every screen. */
object CartStore {
    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    /** The checkout that was just placed, shown on the confirmation screen. */
    var lastCheckout: Checkout? = null

    fun add(item: CartItem) { _cart.value = _cart.value.add(item) }
    fun setQty(listingId: String, qty: Double) { _cart.value = _cart.value.setQty(listingId, qty) }
    fun remove(listingId: String) { _cart.value = _cart.value.remove(listingId) }
    fun clear() { _cart.value = Cart() }
    fun adjustToStock(shortages: List<Shortage>) { _cart.value = _cart.value.applyShortages(shortages) }
}

// ─── Formatting ─────────────────────────────────────────────────────────────

fun formatRupees(amount: Double): String = "₹" + String.format(Locale.US, "%.2f", amount)

/** 2 -> "2", 2.5 -> "2.5" (no trailing zeros). */
fun formatQty(qty: Double): String {
    val r = (qty * 1000).roundToLong() / 1000.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

// ─── Address form ───────────────────────────────────────────────────────────

data class AddressForm(
    val label: String = "Home",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val pincode: String = "",
    val lat: String = "26.9124",
    val lng: String = "75.7873",
    val contactPhone: String = "",
)

sealed interface AddressResult {
    data class Ok(val value: DeliveryAddress) : AddressResult
    data class Invalid(val errors: Map<String, String>) : AddressResult
}

private fun coordinate(text: String, min: Double, max: Double): Double? {
    if (text.isBlank()) return null
    val v = text.trim().toDoubleOrNull() ?: return null
    return if (v in min..max) v else null
}

/** Same rules as the order service, so the server rarely has to reject the address. */
fun validateAddress(form: AddressForm): AddressResult {
    val errors = mutableMapOf<String, String>()
    if (form.line1.isBlank()) errors["line1"] = "Enter the street address"
    if (form.city.isBlank()) errors["city"] = "Enter the city"
    if (!Regex("^\\d{6}$").matches(form.pincode.trim())) errors["pincode"] = "Enter a 6 digit pincode"
    val lat = coordinate(form.lat, -90.0, 90.0)
    val lng = coordinate(form.lng, -180.0, 180.0)
    if (lat == null) errors["lat"] = "Latitude must be between -90 and 90"
    if (lng == null) errors["lng"] = "Longitude must be between -180 and 180"
    val phone = form.contactPhone.trim()
    if (phone.isNotEmpty() && !Regex("^\\+?\\d{10,13}$").matches(phone)) errors["contactPhone"] = "Enter a valid phone"
    if (errors.isNotEmpty() || lat == null || lng == null) return AddressResult.Invalid(errors)
    return AddressResult.Ok(
        DeliveryAddress(
            label = form.label.trim().ifEmpty { null },
            line1 = form.line1.trim(),
            line2 = form.line2.trim().ifEmpty { null },
            city = form.city.trim(),
            pincode = form.pincode.trim(),
            location = GeoPoint.of(lat, lng),
            contactPhone = phone.ifEmpty { null },
        ),
    )
}

// ─── Checkout errors ────────────────────────────────────────────────────────

sealed class CheckoutError(val message: String) {
    class Stock(message: String, val shortages: List<Shortage>) : CheckoutError(message)
    class ListingUnavailable(message: String) : CheckoutError(message)
    class PaymentFailed(message: String) : CheckoutError(message)
    class InProgress(message: String) : CheckoutError(message)
    class AttemptInvalid(message: String) : CheckoutError(message)
    class Unauthorized(message: String) : CheckoutError(message)
    class Validation(message: String, val fields: List<Pair<String, String>>) : CheckoutError(message)
    class Network(message: String) : CheckoutError(message)
    class Server(message: String, val code: String?) : CheckoutError(message)
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Err(val error: CheckoutError) : ApiResult<Nothing>
}

val networkError = CheckoutError.Network("Could not reach the server. Check your connection and try again.")

private fun toShortages(details: JsonElement?): List<Shortage> = (details as? JsonArray)?.mapNotNull { d ->
    val o = d as? JsonObject ?: return@mapNotNull null
    val id = o["listingId"]?.jsonPrimitive?.content ?: return@mapNotNull null
    val req = o["requested"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
    val avail = o["available"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
    Shortage(id, req, avail)
} ?: emptyList()

/** Turn an HTTP status and error body of POST /orders (or the payment intent) into a CheckoutError. */
fun parseCheckoutError(status: Int, body: com.farm.core.api.ErrorBody?): CheckoutError {
    val code = body?.code
    return when {
        status == 401 -> CheckoutError.Unauthorized("Please log in to place an order.")
        status == 402 || code == "PAYMENT_FAILED" ->
            CheckoutError.PaymentFailed("Your payment was declined. No money was taken, please try again.")
        code == "STOCK_UNAVAILABLE" ->
            CheckoutError.Stock("Some items are no longer available in the quantity you chose.", toShortages(body.details))
        code == "LISTING_NOT_AVAILABLE" || code == "LISTING_NOT_FOUND" ->
            CheckoutError.ListingUnavailable("An item in your cart is no longer for sale. Remove it and try again.")
        code == "IDEMPOTENCY_IN_PROGRESS" -> CheckoutError.InProgress("Your order is still being processed. Try again.")
        code in listOf("IDEMPOTENCY_KEY_REUSED", "PAYMENT_INTENT_USED", "PAYMENT_INTENT_NOT_FOUND") ->
            CheckoutError.AttemptInvalid("That checkout attempt cannot be reused. Please try again.")
        status == 422 || code == "VALIDATION_ERROR" -> {
            val fields = (body?.details as? JsonArray)?.mapNotNull { d ->
                val o = d as? JsonObject ?: return@mapNotNull null
                val f = o["field"]?.jsonPrimitive?.content
                val m = o["message"]?.jsonPrimitive?.content
                if (f != null && m != null) f to m else null
            } ?: emptyList()
            CheckoutError.Validation("Please check the details you entered.", fields)
        }
        status == 503 || code == "CATALOG_UNAVAILABLE" ->
            CheckoutError.Network("The shop is busy right now. Please try again shortly.")
        else -> CheckoutError.Server(body?.message ?: "Something went wrong. Please try again.", code)
    }
}

// ─── Checkout session (Idempotency-Key + payment intent kept between taps) ──────────────────

interface CheckoutApi {
    suspend fun createPaymentIntent(amount: Double): ApiResult<String>
    suspend fun placeOrder(key: String, body: CheckoutRequest): ApiResult<Checkout>
}

object RealCheckoutApi : CheckoutApi {
    override suspend fun createPaymentIntent(amount: Double): ApiResult<String> = try {
        val res = OrderApi.createPaymentIntent(PaymentIntentCreate(amount, "FULL"))
        if (res.ok) ApiResult.Ok(res.decode<PaymentIntent>().id) else ApiResult.Err(parseCheckoutError(res.status, res.error()))
    } catch (_: NetworkException) {
        ApiResult.Err(networkError)
    }

    override suspend fun placeOrder(key: String, body: CheckoutRequest): ApiResult<Checkout> = try {
        val res = OrderApi.placeOrder(key, body)
        if (res.ok) ApiResult.Ok(res.decode<Checkout>()) else ApiResult.Err(parseCheckoutError(res.status, res.error()))
    } catch (_: NetworkException) {
        ApiResult.Err(networkError)
    }
}

/** Same cart and address give the same fingerprint; changing either starts a new attempt. */
fun fingerprint(cart: Cart, address: DeliveryAddress, notes: String?): String =
    cart.toOrderItems().sortedBy { it.listingId }.joinToString { "${it.listingId}:${it.qty}" } +
        "|" + Http.encode(address) + "|" + (notes ?: "")

/**
 * Runs the checkout and keeps the Idempotency-Key and payment intent between taps, so a retry
 * after a network error can never create a second order:
 *  - same cart + same address -> same key and intent (the server returns the first result)
 *  - cart or address changed   -> new key and a new payment intent
 *  - payment declined / success -> the attempt is over; the next one starts fresh
 */
class CheckoutSession(
    private val api: CheckoutApi,
    private val feePerFarm: Double,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private class Attempt(val key: String, var intentId: String?, val fingerprint: String)
    private var attempt: Attempt? = null

    val currentKey: String? get() = attempt?.key

    suspend fun submit(cart: Cart, address: DeliveryAddress, notes: String?): ApiResult<Checkout> {
        val print = fingerprint(cart, address, notes)
        val current = attempt?.takeIf { it.fingerprint == print } ?: Attempt(newKey(), null, print).also { attempt = it }

        if (current.intentId == null) {
            when (val intent = api.createPaymentIntent(cart.estimateTotal(feePerFarm))) {
                is ApiResult.Err -> return intent
                is ApiResult.Ok -> current.intentId = intent.data
            }
        }
        val result = api.placeOrder(
            current.key,
            CheckoutRequest(cart.toOrderItems(), address, paymentIntentId = current.intentId!!, notes = notes),
        )
        when (result) {
            is ApiResult.Ok -> attempt = null
            is ApiResult.Err -> if (result.error is CheckoutError.PaymentFailed || result.error is CheckoutError.AttemptInvalid) {
                attempt = null // the server remembers this key; a retry needs a new one
            }
        }
        return result
    }
}
