package com.farm.customer.checkout

import com.farm.core.api.Checkout
import com.farm.core.api.CheckoutRequest
import com.farm.core.api.DeliveryAddress
import com.farm.core.api.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports of the key cases in apps/customer/tests/{cart,checkoutFlow}.test.mjs. */
class CheckoutTest {
    private val tomato = CartItem("l1", "f1", "Farm A", "Tomato", "kg", 32.0, 1.0, maxQty = 5.0)
    private val onion = CartItem("l2", "f2", "Farm B", "Onion", "kg", 20.5, 2.0)
    private val address = DeliveryAddress(line1 = "1 Road", city = "Durg", pincode = "491001", location = GeoPoint.of(21.19, 81.28))

    @Test
    fun addingMergesAndClampsToStock() {
        val cart = Cart().add(tomato).add(tomato.copy(qty = 10.0))
        assertEquals(1, cart.items.size)
        assertEquals(5.0, cart.items[0].qty, 0.0)
    }

    @Test
    fun totalsAddOneDeliveryFeePerFarm() {
        val cart = Cart().add(tomato).add(onion)
        assertEquals(73.0, cart.subtotal(), 0.001)
        assertEquals(2, cart.groupByFarm().size)
        assertEquals(113.0, cart.estimateTotal(20.0), 0.001)
    }

    @Test
    fun shortagesCapOrRemoveLines() {
        val cart = Cart().add(tomato.copy(qty = 3.0)).add(onion)
            .applyShortages(listOf(Shortage("l1", 3.0, 1.0), Shortage("l2", 2.0, 0.0)))
        assertEquals(listOf("l1"), cart.items.map { it.listingId })
        assertEquals(1.0, cart.items[0].qty, 0.0)
    }

    private class FakeApi(var placeResults: MutableList<ApiResult<Checkout>>) : CheckoutApi {
        var intents = 0
        val keys = mutableListOf<String>()
        override suspend fun createPaymentIntent(amount: Double): ApiResult<String> = ApiResult.Ok("pi-${++intents}")
        override suspend fun placeOrder(key: String, body: CheckoutRequest): ApiResult<Checkout> {
            keys += key
            return placeResults.removeAt(0)
        }
    }

    @Test
    fun retryAfterNetworkErrorReusesKeyAndIntent() = runBlocking {
        val api = FakeApi(mutableListOf(ApiResult.Err(networkError), ApiResult.Ok(Checkout("c1"))))
        var n = 0
        val session = CheckoutSession(api, 20.0) { "key-${++n}" }
        val cart = Cart().add(tomato)
        session.submit(cart, address, null)
        val second = session.submit(cart, address, null)
        assertTrue(second is ApiResult.Ok)
        assertEquals(listOf("key-1", "key-1"), api.keys)
        assertEquals(1, api.intents)
        assertNull(session.currentKey)
    }

    @Test
    fun changedCartOrDeclinedPaymentStartsANewAttempt() = runBlocking {
        val api = FakeApi(
            mutableListOf(
                ApiResult.Err(networkError),
                ApiResult.Err(CheckoutError.PaymentFailed("declined")),
                ApiResult.Ok(Checkout("c1")),
            ),
        )
        var n = 0
        val session = CheckoutSession(api, 20.0) { "key-${++n}" }
        session.submit(Cart().add(tomato), address, null)
        session.submit(Cart().add(tomato).add(onion), address, null) // cart changed -> new key
        session.submit(Cart().add(tomato).add(onion), address, null) // after decline -> new key
        assertEquals(listOf("key-1", "key-2", "key-3"), api.keys)
        assertEquals(3, api.intents)
    }

    @Test
    fun addressValidationMatchesServerRules() {
        val bad = validateAddress(AddressForm(line1 = "", city = "Durg", pincode = "12", lat = "100"))
        assertTrue(bad is AddressResult.Invalid)
        assertEquals(setOf("line1", "pincode", "lat"), (bad as AddressResult.Invalid).errors.keys)
        val ok = validateAddress(AddressForm(line1 = "1 Road", city = "Durg", pincode = "491001"))
        assertTrue(ok is AddressResult.Ok)
        assertEquals(listOf(75.7873, 26.9124), (ok as AddressResult.Ok).value.location.coordinates)
    }
}
