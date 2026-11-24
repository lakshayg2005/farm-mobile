package com.farm.customer.browse

import com.farm.core.api.Http
import com.farm.core.api.ListingFeatureCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTest {
    private val searchJson = """
        {"type":"FeatureCollection","nextCursor":"abc",
         "features":[
          {"type":"Feature","id":"l1","geometry":{"type":"Point","coordinates":[81.28,21.19]},
           "properties":{"id":"l1","farmId":"f1","farmName":"Green Acres","cropId":"tomato","cropName":"Tomato",
             "unit":"kg","pricePerUnit":32.5,"qtyAvailable":40,"harvestDate":"2026-11-20",
             "distanceM":2400,"district":"Durg","village":"Patan","farmRating":4.25,"farmRatingCount":12,
             "mediaIds":["m1"],"someNewField":true}},
          {"type":"Feature","id":"l2","geometry":null,
           "properties":{"id":"l2","farmName":"New Farm","cropId":"onion","cropName":"Onion","farmRating":null,"farmRatingCount":0}}
         ]}
    """.trimIndent()

    @Test fun parsesGeoJsonLongitudeFirst() {
        val page = Http.json.decodeFromString(ListingFeatureCollection.serializer(), searchJson)
        assertEquals("abc", page.nextCursor)
        val first = page.features[0].toListing(listOf(0.0, 0.0))
        assertEquals(81.28, first.pickupCoordinates[0], 0.0) // lng
        assertEquals(21.19, first.pickupCoordinates[1], 0.0) // lat
        assertEquals(4.25, first.farmRating!!, 0.0)
        assertEquals(12, first.farmRatingCount)
        assertEquals("Patan", first.village)
    }

    @Test fun featureWithoutPointUsesFallbackAndUnratedFarm() {
        val page = Http.json.decodeFromString(ListingFeatureCollection.serializer(), searchJson)
        val second = page.features[1].toListing(listOf(81.0, 21.0))
        assertEquals(listOf(81.0, 21.0), second.pickupCoordinates)
        assertNull(second.farmRating)
    }

    @Test fun radiusIsClampedToWhatTheCatalogAccepts() {
        assertEquals(100, clampRadius(250))
        assertEquals(1, clampRadius(0))
        assertEquals(25, clampRadius(25))
    }

    @Test fun ratingFormatting() {
        assertEquals("4.3", formatRating(4.25))
        assertEquals("★ 4.0 (3)", ratingLabel(4.0, 3))
        assertEquals("Not rated yet", ratingLabel(null, 0))
        assertEquals("★★★★☆", ratingStars(4.25))
        assertEquals("★★★★★", ratingStars(4.6))
    }

    @Test fun onlyTheTextQueryStaysOnTheDevice() {
        val base = FilterState()
        assertFalse(needsRefetch(base, base.copy(query = "tom")))
        assertTrue(needsRefetch(base, base.copy(organicOnly = true)))
        assertTrue(needsRefetch(base, base.copy(sort = SortOption.NEWEST)))
        assertTrue(needsRefetch(base, base.copy(maxPrice = 50.0)))
    }

    @Test fun queryMatchesCropFarmAndPlace() {
        val page = Http.json.decodeFromString(ListingFeatureCollection.serializer(), searchJson)
        val l = page.features[0].toListing(listOf(0.0, 0.0))
        assertTrue(matchesQuery(l, "green"))
        assertTrue(matchesQuery(l, "patan"))
        assertTrue(matchesQuery(l, " "))
        assertFalse(matchesQuery(l, "mango"))
    }

    @Test fun distanceFormatting() {
        assertEquals("450 m", formatDistance(450.0))
        assertEquals("2.4 km", formatDistance(2400.0))
        assertEquals("12 km", formatDistance(12000.0))
    }

    @Test fun photoBadgeFollowsTheCameraLocation() {
        assertNull(photoVerification(null)) // no photo: no badge at all
        val near = photoVerification(ProducePhoto("m", isGeoVerified = true, geoStatus = "VERIFIED", distanceM = 300.0))!!
        assertTrue(near.isVerified)
        val screenshot = photoVerification(ProducePhoto("m", geoStatus = "NO_GPS"))!!
        assertFalse(screenshot.isVerified)
        assertEquals("Unverified", screenshot.shortLabel)
        assertTrue(screenshot.description.contains("screenshot"))
        val far = photoVerification(ProducePhoto("m", geoStatus = "TOO_FAR", distanceM = 4200.0))!!
        assertEquals("Taken 4.2 km from the farm, not at it.", far.description)
        assertTrue(photoVerification(ProducePhoto("m", geoStatus = "PENDING"))!!.pending)
    }
}
