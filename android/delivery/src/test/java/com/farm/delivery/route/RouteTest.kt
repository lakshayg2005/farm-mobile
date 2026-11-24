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
    fun dropStopCarriesCustomerAddressAndNeverTheOtp() {
        val r = toRouteStop(
            stop(id = "s2", stopType = "DROP", seq = 2, status = "ARRIVED", customerName = "Asha Verma", address = "Flat 4, Raipur", otp = "4821"),
        )
        assertEquals(StopType.DROP, r.type)
        assertEquals("Customer: Asha Verma", r.label)
        assertEquals("Flat 4, Raipur", r.address)
        assertEquals(StopStatus.ARRIVED, r.status)
        assertEquals(true, r.status.open) // arrived but not delivered yet: still the stop to work on
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

    @Test
    fun dialUriKeepsDigitsAndPlus() {
        assertEquals("tel:+919400000001", dialUri("+91 94000-00001"))
        assertEquals(null, dialUri("  "))
        assertEquals(null, dialUri(null))
    }

    @Test
    fun phoneAndNotesComeFromTheStop() {
        val r = toRouteStop(stop(stopType = "DROP").copy(customerPhone = " +919400000002 ", notes = "Ring twice"))
        assertEquals("+919400000002", r.phone)
        assertEquals("Ring twice", r.notes)
    }

    @Test
    fun routeLengthAddsLegsInOrder() {
        val a = toRouteStop(stop(id = "a").copy(lat = 21.0, lng = 81.0))
        val b = toRouteStop(stop(id = "b").copy(lat = 21.01, lng = 81.0))
        assertEquals(1112.0, routeLengthM(listOf(a, b)), 5.0)
        assertEquals(0.0, routeLengthM(listOf(a)), 0.0)
    }

    @Test
    fun mapDrawsTheRoadPathWhenTheBackendSendsOne() {
        val stops = listOf(toRouteStop(stop()))
        val pool = Pool(id = "p1", routePath = listOf(listOf(21.0, 81.0), listOf(21.1, 81.1), listOf(21.2, 81.2)))
        assertEquals(listOf(21.0 to 81.0, 21.1 to 81.1, 21.2 to 81.2), routeLine(pool, stops))
    }

    @Test
    fun mapFallsBackToStraightLinesBetweenStopsWithoutARoadPath() {
        val stops = listOf(toRouteStop(stop(id = "a")), toRouteStop(stop(id = "b", seq = 2)))
        assertEquals(listOf(21.25 to 81.63, 21.25 to 81.63), routeLine(Pool(id = "p1"), stops))
        assertEquals(listOf(21.25 to 81.63, 21.25 to 81.63), routeLine(null, stops))
    }

    @Test fun historyTalliesAndDays() {
        val stops = listOf(
            toRouteStop(stop("a", status = "DONE")),
            toRouteStop(stop("b", stopType = "DROP", seq = 2, status = "DONE")),
            toRouteStop(stop("c", stopType = "DROP", seq = 3, status = "SKIPPED")),
        )
        assertEquals(RouteTally(3, 2, 1, 0), tally(stops))
        assertEquals("2 of 3 stops done · 1 skipped", tallyText(tally(stops)))
        assertEquals("All 2 stops done", tallyText(tally(stops.take(2))))

        val today = LocalDate.parse("2026-11-24")
        assertEquals("Today", historyDayLabel(today, today))
        assertEquals("Yesterday", historyDayLabel(today.minusDays(1), today))

        val pools = listOf(Pool("old", poolDate = "2026-11-20"), Pool("t1", poolDate = "2026-11-24"), Pool("t2", poolDate = "2026-11-24"))
        assertEquals(listOf("2026-11-24" to listOf("t1", "t2"), "2026-11-20" to listOf("old")), groupByDay(pools).map { (d, p) -> d to p.map { it.id } })
    }
}
