package com.farm.delivery.route

import com.farm.core.api.Pool
import com.farm.core.api.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Port of apps/delivery/tests/route.test.mjs. */
class RouteTest {
    private fun stop(
        id: String = "s1", stopType: String = "PICKUP", seq: Int = 1, status: String = "PENDING",
        weightKg: Double? = null, customerName: String? = null, address: String? = null, otp: String? = null,
    ) = Stop(
        id = id, poolId = "p1", orderId = "aaaaaaaa-1111-2222-3333-444444444444", stopType = stopType, seq = seq,
        lat = 21.25, lng = 81.63, status = status, weightKg = weightKg, customerName = customerName,
        address = address, otp = otp,
    )

    @Test
    fun pickupStopMapsOntoTheScreenShape() {
        assertEquals(
            RouteStop(
                id = "s1", orderId = "aaaaaaaa-1111-2222-3333-444444444444", seq = 1, type = StopType.PICKUP,
                status = StopStatus.PENDING, label = "Farm pickup for order #aaaaaaaa", address = "21.25000, 81.63000",
                lat = 21.25, lng = 81.63, customerName = "Farm", items = listOf("Order #aaaaaaaa · 12.5 kg"),
            ),
            toRouteStop(stop(weightKg = 12.5)),
        )
    }

    @Test
    fun dropStopCarriesCustomerAddressAndOtp() {
        val r = toRouteStop(
            stop(id = "s2", stopType = "DROP", seq = 2, status = "ARRIVED", customerName = "Asha Verma", address = "Flat 4, Raipur", otp = "4821"),
        )
        assertEquals(StopType.DROP, r.type)
        assertEquals(StopStatus.PENDING, r.status) // arrived but not delivered yet
        assertEquals("Customer: Asha Verma", r.label)
        assertEquals("Flat 4, Raipur", r.address)
        assertEquals("4821", r.otp)
        assertEquals("Customer: order #aaaaaaaa", toRouteStop(stop(stopType = "DROP", customerName = "  ")).label)
    }

    @Test
    fun stopsComeInSeqOrderAndSummaryFindsNext() {
        val pool = Pool(
            id = "p1", poolDate = "2026-12-03", status = "IN_PROGRESS",
            stops = listOf(
                stop(id = "d1", stopType = "DROP", seq = 3),
                stop(id = "p1", seq = 1, status = "DONE"),
                stop(id = "p2", seq = 2, status = "SKIPPED"),
            ),
        )
        val stops = routeStops(pool)
        assertEquals(listOf("p1", "p2", "d1"), stops.map { it.id })
        val summary = routeSummary(stops)
        assertEquals(2, summary.pickups)
        assertEquals(1, summary.drops)
        assertEquals(1, summary.done)
        assertFalse(summary.finished)
        assertEquals("d1", summary.next?.id)

        val after = routeStops(markStopDone(pool, "d1"))
        assertTrue(routeSummary(after).finished)
        assertNull(routeSummary(after).next)
        assertEquals("PENDING", pool.stops[0].status) // the loaded pool is not mutated
    }

    @Test
    fun noPoolMeansNoStops() {
        assertEquals(emptyList<RouteStop>(), routeStops(null))
        assertFalse(routeSummary(emptyList()).finished)
    }

    @Test
    fun localIsoDateUsesTheCalendarDay() {
        assertEquals("2026-01-05", localIsoDate(LocalDate.of(2026, 1, 5)))
    }
}
