package com.farm.customer.land

import com.farm.core.api.LandApi
import com.farm.core.api.PlotOut
import com.farm.core.api.PlotSeasonFeature
import com.farm.core.api.ReservationCreate
import com.farm.core.api.ReservationOut
import com.farm.customer.browse.haversineM
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

@Serializable
data class PlotSeasonItem(
    val id: String,
    val plotId: String = "",
    val season: String,
    val startDate: String = "",
    val endDate: String = "",
    val pricePerKg: Double = 0.0,
    val allowedCrops: List<String> = emptyList(),
)

@Serializable
data class PlotItem(
    val id: String,
    val farmerId: String = "",
    /** Not returned by the land API yet; shown only when known. */
    val farmerName: String = "",
    val farmerPhone: String? = null,
    val title: String,
    val description: String? = null,
    val areaAcres: Double = 0.0,
    val soilType: String? = null,
    val waterSource: String? = null,
    val status: String = "AVAILABLE",
    /** Not returned by the plot search yet; shown only when known. */
    val village: String = "",
    val district: String = "",
    val distanceM: Double = 0.0,
    /** [lng, lat] */
    val centroid: List<Double> = listOf(0.0, 0.0),
    val boundary: List<List<List<Double>>> = emptyList(),
    val seasons: List<PlotSeasonItem> = emptyList(),
) {
    val minPrice: Double? get() = seasons.minOfOrNull { it.pricePerKg }
}

@Serializable
data class ReservationItem(
    val id: String,
    val plotId: String = "",
    val plotTitle: String = "",
    val plotSeasonId: String = "",
    val seasonName: String = "",
    val farmerId: String = "",
    val farmerName: String = "",
    val farmerPhone: String? = null,
    val cropId: String = "",
    val cropName: String = "",
    val expectedQtyKg: Double = 0.0,
    val status: String = "PENDING",
    val deposit: Double = 0.0,
    val total: Double = 0.0,
    val createdAt: String = "",
    /** When a PENDING request lapses if the farmer does not answer. */
    val expiresAt: String? = null,
)

enum class LandSortOption { DISTANCE, ACRES_DESC, ACRES_ASC, PRICE_ASC, PRICE_DESC }

data class LandFilterState(
    val query: String = "",
    val season: String? = null,
    val crop: String? = null,
    val soilType: String? = null,
    val radiusKm: Int = DEFAULT_LAND_RADIUS_KM,
    val minAcres: Double? = null,
    val maxAcres: Double? = null,
    /** Only plots with a season at or under this price (rupees per kg). */
    val maxPricePerKg: Double? = null,
    val sort: LandSortOption = LandSortOption.DISTANCE,
)

/** The search radius chosen when nothing is set. */
const val DEFAULT_LAND_RADIUS_KM = 50

/** How many filters differ from the defaults (shown as a count on the Filters button). */
fun activeLandFilters(f: LandFilterState): Int = listOf(
    f.radiusKm != DEFAULT_LAND_RADIUS_KM,
    f.crop != null && f.crop != "ALL",
    f.soilType != null && f.soilType != "ALL",
    (f.minAcres ?: 0.0) > 0 || (f.maxAcres ?: 0.0) > 0,
    (f.maxPricePerKg ?: 0.0) > 0,
    f.sort != LandSortOption.DISTANCE,
).count { it }

/** Every crop the loaded plots allow, sorted, for the crop filter. */
fun cropsOf(plots: List<PlotItem>): List<String> =
    plots.flatMap { p -> p.seasons.flatMap { it.allowedCrops } }.map { it.lowercase() }.distinct().sorted()

fun filterPlots(plots: List<PlotItem>, f: LandFilterState): List<PlotItem> = plots.filter { plot ->
    val q = f.query.trim().lowercase()
    if (q.isNotEmpty()) {
        val hit = plot.title.lowercase().contains(q) || plot.description?.lowercase()?.contains(q) == true ||
            plot.farmerName.lowercase().contains(q) || plot.district.lowercase().contains(q) ||
            plot.village.lowercase().contains(q) || plot.seasons.any { s -> s.allowedCrops.any { it.lowercase().contains(q) } }
        if (!hit) return@filter false
    }
    if (f.season != null && f.season != "ALL" && plot.seasons.none { it.season.equals(f.season, true) }) return@filter false
    if (f.crop != null && f.crop != "ALL" && plot.seasons.none { s -> s.allowedCrops.any { it.equals(f.crop, true) } }) return@filter false
    if (f.soilType != null && f.soilType != "ALL" && plot.soilType?.equals(f.soilType, true) != true) return@filter false
    if (f.radiusKm > 0 && plot.distanceM > f.radiusKm * 1000) return@filter false
    if (f.minAcres != null && f.minAcres > 0 && plot.areaAcres < f.minAcres) return@filter false
    if (f.maxAcres != null && f.maxAcres > 0 && plot.areaAcres > f.maxAcres) return@filter false
    if (f.maxPricePerKg != null && f.maxPricePerKg > 0 && (plot.minPrice ?: Double.MAX_VALUE) > f.maxPricePerKg) return@filter false
    true
}

fun sortPlots(plots: List<PlotItem>, sort: LandSortOption): List<PlotItem> = when (sort) {
    LandSortOption.DISTANCE -> plots.sortedBy { it.distanceM }
    LandSortOption.ACRES_DESC -> plots.sortedByDescending { it.areaAcres }
    LandSortOption.ACRES_ASC -> plots.sortedBy { it.areaAcres }
    LandSortOption.PRICE_ASC -> plots.sortedBy { it.minPrice ?: 999.0 }
    LandSortOption.PRICE_DESC -> plots.sortedByDescending { it.seasons.maxOfOrNull { s -> s.pricePerKg } ?: 0.0 }
}

private fun r2(v: Double) = (v * 100).roundToLong() / 100.0

/** Deposit (20%) and total estimated reservation cost. */
fun reservationEstimate(pricePerKg: Double, qtyKg: Double): Pair<Double, Double> {
    val total = r2(pricePerKg * maxOf(0.0, qtyKg))
    return r2(total * 0.2) to total
}

/** "wheat" -> "Wheat", "green_chilli" -> "Green chilli". */
fun cropTitle(cropId: String): String = cropId.replace('_', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }

/** A reservation as the server returned it; [plot] and [season] fill the names when known. */
fun ReservationOut.toItem(plot: PlotItem? = null, season: PlotSeasonItem? = null): ReservationItem = ReservationItem(
    id = id, plotId = plot?.id ?: "", plotTitle = plot?.title ?: plotTitle ?: "",
    plotSeasonId = plotSeasonId, seasonName = season?.season ?: seasonName ?: "",
    farmerId = farmerId, farmerName = plot?.farmerName ?: "", farmerPhone = plot?.farmerPhone,
    cropId = cropId, cropName = cropTitle(cropId), expectedQtyKg = expectedQtyKg, status = status,
    deposit = deposit ?: 0.0, total = total ?: 0.0, createdAt = createdAt ?: "", expiresAt = expiresAt,
)

/** Season names the land service uses (plot-seasons are "Kharif", "Rabi" or "Zaid"). */
val SEASON_NAMES = listOf("Kharif", "Rabi", "Zaid")

/** Plot-seasons asked for per search page (the server's maximum). */
const val PLOT_PAGE_SIZE = 100

/** The search radius the server accepts (1 to 200 km). */
fun clampPlotRadius(km: Int): Int = km.coerceIn(1, 200)

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/** A season can be reserved up to and including its last day, India time (the server's rule). */
fun seasonEnded(endDate: String, today: LocalDate = LocalDate.now(IST)): Boolean =
    runCatching { LocalDate.parse(endDate.take(10)).isBefore(today) }.getOrDefault(false)

/** The middle of a plot's outer ring, as [lng, lat]; null without a usable polygon. */
fun polygonCentroid(rings: List<List<List<Double>>>): List<Double>? {
    val ring = rings.firstOrNull()?.filter { it.size >= 2 } ?: return null
    val points = if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
    if (points.isEmpty()) return null
    return listOf(points.sumOf { it[0] } / points.size, points.sumOf { it[1] } / points.size)
}

/**
 * Turns search hits (one per plot-season) into plots with their open seasons, keeping the
 * server's order, and adds them to [existing] when paging. Ended seasons are left out, and a plot
 * with no open season is not shown.
 */
fun mergePlotFeatures(
    existing: List<PlotItem>,
    features: List<PlotSeasonFeature>,
    lat: Double,
    lng: Double,
    today: LocalDate = LocalDate.now(IST),
): List<PlotItem> {
    val plots = LinkedHashMap<String, PlotItem>()
    existing.forEach { plots[it.id] = it }
    for (f in features) {
        val p = f.properties
        val season = PlotSeasonItem(p.plotSeasonId, f.id, p.season, p.startDate, p.endDate, p.price, p.allowedCrops)
        val known = plots[f.id]
        if (known != null) {
            if (known.seasons.none { it.id == season.id }) plots[f.id] = known.copy(seasons = known.seasons + season)
            continue
        }
        val rings = f.geometry?.coordinates ?: emptyList()
        val centre = polygonCentroid(rings)
        plots[f.id] = PlotItem(
            id = f.id, farmerId = p.farmerId, title = p.title, areaAcres = p.areaAcres,
            distanceM = centre?.let { haversineM(lat, lng, it[1], it[0]) } ?: 0.0,
            centroid = centre ?: listOf(lng, lat), boundary = rings, seasons = listOf(season),
        )
    }
    return plots.values
        .map { plot -> plot.copy(seasons = plot.seasons.filterNot { seasonEnded(it.endDate, today) }) }
        .filter { it.seasons.isNotEmpty() }
}

/** Message for a refused POST /land/reservations. */
fun reserveErrorMessage(code: String?, serverMessage: String?): String = when (code) {
    "SEASON_ENDED" -> "This season has already ended. Pick another season."
    "CROP_NOT_ALLOWED" -> "That crop is not allowed in this season. Pick one of the listed crops."
    "PLOT_SEASON_ALREADY_RESERVED" -> "Someone has just reserved this season. Pick another season or plot."
    "PLOT_SEASON_NOT_FOUND", "PLOT_NOT_FOUND" -> "This plot is no longer available."
    else -> serverMessage ?: "Could not reserve the plot. Try again."
}

/** One page of plots, and where the next page starts (null when this was the last one). */
data class PlotPage(val plots: List<PlotItem>, val nextOffset: Int?)

/** The land service only. A failed call throws; nothing is invented. */
object LandRepository {
    /** Open plot-seasons around a point, grouped into plots; pass [existing] and [offset] for more. */
    suspend fun searchPlots(
        lat: Double,
        lng: Double,
        radiusKm: Int,
        season: String?,
        crop: String?,
        offset: Int = 0,
        existing: List<PlotItem> = emptyList(),
    ): PlotPage {
        val res = LandApi.searchPlots(lat, lng, clampPlotRadius(radiusKm), season, crop, PLOT_PAGE_SIZE, offset)
        return PlotPage(
            mergePlotFeatures(existing, res.features, lat, lng),
            if (res.features.size == PLOT_PAGE_SIZE) offset + PLOT_PAGE_SIZE else null,
        )
    }

    /** Open plot-seasons inside the visible map box [bbox] = west, south, east, north. */
    suspend fun searchArea(bbox: List<Double>, season: String?, crop: String?, fromLat: Double, fromLng: Double): PlotPage {
        val res = LandApi.searchPlots(null, null, null, season, crop, PLOT_PAGE_SIZE, 0, bbox)
        return PlotPage(mergePlotFeatures(emptyList(), res.features, fromLat, fromLng), null)
    }

    /** Description, soil and water of one plot (the search does not carry them). */
    suspend fun plotDetail(plotId: String): PlotOut = LandApi.getPlot(plotId)

    suspend fun fetchReservations(): List<ReservationItem> = LandApi.getMyReservations().map { it.toItem() }

    /** POST /land/reservations with the plot-season id. Throws the server's error (e.g. SEASON_ENDED). */
    suspend fun submitReservation(plot: PlotItem, season: PlotSeasonItem, cropId: String, qtyKg: Double): ReservationItem =
        LandApi.createReservation(ReservationCreate(season.id, cropId, qtyKg)).toItem(plot, season)
}
