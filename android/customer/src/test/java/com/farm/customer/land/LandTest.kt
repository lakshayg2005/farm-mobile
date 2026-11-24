package com.farm.customer.land

import com.farm.core.api.Http
import com.farm.core.api.PlotSearchResult
import com.farm.customer.reservation.canCancelReservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Land search and reserve rules (#41), against the shape GET /land/plots/search really returns. */
class LandTest {
    private val today = LocalDate.parse("2026-11-22")

    // one feature per plot-season; the geometry is the plot's boundary ([lng, lat])
    private val reply = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","id":"p1","geometry":{"type":"Polygon","coordinates":[[[81.28,21.19],[81.30,21.19],[81.30,21.21],[81.28,21.21],[81.28,21.19]]]},
           "properties":{"title":"Durg riverside plot","areaAcres":2.5,"farmerId":"f1","plotSeasonId":"s-rabi","season":"Rabi",
             "startDate":"2026-11-15","endDate":"2027-03-31","price":40.0,"allowedCrops":["tomato","cauliflower"]}},
          {"type":"Feature","id":"p1","geometry":{"type":"Polygon","coordinates":[[[81.28,21.19],[81.30,21.19],[81.30,21.21],[81.28,21.21],[81.28,21.19]]]},
           "properties":{"title":"Durg riverside plot","areaAcres":2.5,"farmerId":"f1","plotSeasonId":"s-kharif-old","season":"Kharif",
             "startDate":"2026-06-15","endDate":"2026-10-31","price":35.0,"allowedCrops":["rice"]}},
          {"type":"Feature","id":"p2","geometry":{"type":"Polygon","coordinates":[[[81.60,21.25],[81.62,21.25],[81.62,21.27],[81.60,21.25]]]},
           "properties":{"title":"Raipur field","areaAcres":1.0,"farmerId":"f2","plotSeasonId":"s-zaid","season":"Zaid",
             "startDate":"2027-04-01","endDate":"2027-06-15","price":30.0,"allowedCrops":["okra"]}},
          {"type":"Feature","id":"p3","geometry":null,
           "properties":{"title":"Old plot","areaAcres":3.0,"farmerId":"f3","plotSeasonId":"s-old","season":"Kharif",
             "startDate":"2026-06-15","endDate":"2026-10-31","price":30.0,"allowedCrops":[]}}
        ]}
    """.trimIndent()

    @Test
    fun featuresAreGroupedIntoPlotsWithOnlyOpenSeasons() {
        val result = Http.json.decodeFromString<PlotSearchResult>(reply)
        val plots = mergePlotFeatures(emptyList(), result.features, 21.19, 81.28, today)
        assertEquals(listOf("p1", "p2"), plots.map { it.id }) // p3 has only an ended season
        val p1 = plots[0]
        assertEquals(listOf("s-rabi"), p1.seasons.map { it.id }) // last Kharif ended
        assertEquals("Rabi", p1.seasons[0].season)
        assertEquals(40.0, p1.seasons[0].pricePerKg, 0.0)
        assertEquals("p1", p1.seasons[0].plotId)
        assertEquals(81.29, p1.centroid[0], 1e-9)
        assertEquals(21.20, p1.centroid[1], 1e-9)
        assertTrue(p1.distanceM in 1_000.0..2_000.0)
    }

    @Test
    fun pagingAddsSeasonsToPlotsAlreadyShown() {
        val result = Http.json.decodeFromString<PlotSearchResult>(reply)
        val first = mergePlotFeatures(emptyList(), result.features.take(1), 21.19, 81.28, today)
        val more = Http.json.decodeFromString<PlotSearchResult>(
            reply.replace("\"s-rabi\"", "\"s-zaid-p1\"").replace("\"endDate\":\"2027-03-31\"", "\"endDate\":\"2027-06-15\""),
        )
        val merged = mergePlotFeatures(first, more.features.take(1), 21.19, 81.28, today)
        assertEquals(1, merged.size)
        assertEquals(listOf("s-rabi", "s-zaid-p1"), merged[0].seasons.map { it.id })
    }

    @Test
    fun aSeasonCanBeReservedOnItsLastDay() {
        assertFalse(seasonEnded("2026-11-22", today))
        assertTrue(seasonEnded("2026-11-21", today))
        assertFalse(seasonEnded("not a date", today))
    }

    @Test
    fun centroidHandlesOpenAndClosedRings() {
        assertEquals(listOf(1.0, 1.0), polygonCentroid(listOf(listOf(listOf(0.0, 0.0), listOf(2.0, 0.0), listOf(2.0, 2.0), listOf(0.0, 2.0)))))
        assertNull(polygonCentroid(emptyList()))
    }

    @Test
    fun radiusIsClampedToWhatTheServerAccepts() {
        assertEquals(200, clampPlotRadius(500))
        assertEquals(1, clampPlotRadius(0))
        assertEquals(25, clampPlotRadius(25))
    }

    @Test
    fun reserveErrorsAreSpecific() {
        assertEquals("This season has already ended. Pick another season.", reserveErrorMessage("SEASON_ENDED", "x"))
        assertTrue(reserveErrorMessage("CROP_NOT_ALLOWED", "x").startsWith("That crop is not allowed"))
        assertTrue(reserveErrorMessage("PLOT_SEASON_ALREADY_RESERVED", "x").startsWith("Someone has just reserved"))
        assertEquals("Server says no", reserveErrorMessage("OTHER", "Server says no"))
    }

    @Test
    fun cancelIsOfferedWhileTheReservationIsLive() {
        listOf("PENDING", "ACCEPTED", "ACTIVE", "HARVEST_READY").forEach { assertTrue(it, canCancelReservation(it)) }
        listOf("FULFILLING", "REJECTED", "CANCELLED", "EXPIRED").forEach { assertFalse(it, canCancelReservation(it)) }
    }

    @Test
    fun seasonChipsUseTheServerNames() {
        assertEquals(listOf("Kharif", "Rabi", "Zaid"), SEASON_NAMES)
    }

    @Test fun priceAndRadiusFiltersAndTheActiveCount() {
        fun plot(id: String, price: Double, km: Double) = PlotItem(
            id = id, title = id, areaAcres = 2.0, distanceM = km * 1000,
            seasons = listOf(PlotSeasonItem("s$id", id, "Rabi", pricePerKg = price, allowedCrops = listOf("wheat", "Mustard"))),
        )
        val plots = listOf(plot("cheap", 20.0, 5.0), plot("dear", 60.0, 5.0), plot("far", 20.0, 80.0))
        assertEquals(listOf("cheap", "far"), filterPlots(plots, LandFilterState(radiusKm = 100, maxPricePerKg = 30.0)).map { it.id })
        assertEquals(listOf("cheap", "dear"), filterPlots(plots, LandFilterState(radiusKm = 10)).map { it.id })
        assertEquals(0, activeLandFilters(LandFilterState()))
        assertEquals(3, activeLandFilters(LandFilterState(radiusKm = 10, maxPricePerKg = 30.0, sort = LandSortOption.PRICE_ASC)))
        assertEquals(listOf("mustard", "wheat"), cropsOf(plots))
    }
}
