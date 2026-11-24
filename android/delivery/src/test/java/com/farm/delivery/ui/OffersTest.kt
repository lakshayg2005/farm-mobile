package com.farm.delivery.ui

import com.farm.core.api.ApiException
import com.farm.core.api.OfferPlace
import com.farm.core.api.OfferStop
import com.farm.core.api.RouteOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffersTest {
    private val offer = RouteOffer(
        poolId = "p1", poolDate = "2026-11-24", stopCount = 4, pickupCount = 2, dropCount = 2, totalKm = 12.46,
        distanceKm = 1.24, firstPickup = OfferPlace(21.0, 81.0, "Patan farm"),
    )

    @Test fun countsAndDistances() {
        assertEquals("2 pickups · 2 drops", offerCounts(offer))
        assertEquals("1 pickup · 0 drops", offerCounts(RouteOffer("p", stopCount = 1, pickupCount = 1)))
        assertEquals("1.2 km", formatDistance(1.24))
        assertEquals("850 m", formatDistance(0.85))
    }

    @Test fun tripUsesTheServersFigures() {
        val trip = offerTrip(offer, meLat = 30.0, meLng = 90.0)
        assertEquals(1.24, trip.toStartKm!!, 1e-9)
        assertEquals(12.46, trip.routeKm!!, 1e-9)
        assertEquals(13.70, trip.totalKm!!, 1e-9)
    }

    @Test fun tripIsMeasuredFromTheStopsWhenTheServerHasNoFigures() {
        // 0.01° of latitude is about 1.11 km.
        val o = RouteOffer(
            "p",
            stops = listOf(OfferStop(2, "DROP", 21.02, 81.0), OfferStop(1, "PICKUP", 21.01, 81.0)),
        )
        val trip = offerTrip(o, meLat = 21.0, meLng = 81.0)
        assertEquals(1.11, trip.toStartKm!!, 0.01)
        assertEquals(1.11, trip.routeKm!!, 0.01)
        assertEquals(2.22, trip.totalKm!!, 0.02)
    }

    @Test fun tripIsUnknownWithoutAnything() {
        val trip = offerTrip(RouteOffer("p"))
        assertNull(trip.toStartKm)
        assertNull(trip.routeKm)
        assertNull(trip.totalKm)
    }

    @Test fun losingTheRaceIsRecognised() {
        assertTrue(isTaken(ApiException("taken", "POOL_TAKEN", 409)))
        assertFalse(isTaken(ApiException("nope", "OFFER_NOT_FOUND", 404)))
        assertFalse(isTaken(null))
    }
}
