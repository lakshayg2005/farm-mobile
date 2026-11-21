package com.farm.customer.land

import android.content.Context
import com.farm.core.api.Http
import com.farm.core.api.LandApi
import com.farm.core.api.ReservationCreate
import com.farm.customer.browse.haversineM
import kotlinx.serialization.Serializable
import java.time.LocalDate
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
    val farmerName: String = "Farmer Partner",
    val farmerPhone: String? = null,
    val title: String,
    val description: String? = null,
    val areaAcres: Double = 0.0,
    val soilType: String = "Loam",
    val waterSource: String = "Borewell",
    val status: String = "AVAILABLE",
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
data class TimelinePhoto(
    val id: String,
    val url: String = "",
    val thumbUrl: String = "",
    val isGeoVerified: Boolean = false,
    val distanceM: Double? = null,
)

@Serializable
data class CropTimelineItem(
    val id: String,
    val stage: String,
    val stageTitle: String = "",
    val note: String? = null,
    val createdAt: String = "",
    val photos: List<TimelinePhoto> = emptyList(),
)

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
    val updates: List<CropTimelineItem> = emptyList(),
)

enum class LandSortOption { DISTANCE, ACRES_DESC, ACRES_ASC, PRICE_ASC, PRICE_DESC }

data class LandFilterState(
    val query: String = "",
    val season: String? = null,
    val crop: String? = null,
    val soilType: String? = null,
    val radiusKm: Int = 25,
    val minAcres: Double? = null,
    val maxAcres: Double? = null,
    val sort: LandSortOption = LandSortOption.DISTANCE,
)

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
    if (f.soilType != null && f.soilType != "ALL" && !plot.soilType.equals(f.soilType, true)) return@filter false
    if (f.radiusKm > 0 && plot.distanceM > f.radiusKm * 1000) return@filter false
    if (f.minAcres != null && f.minAcres > 0 && plot.areaAcres < f.minAcres) return@filter false
    if (f.maxAcres != null && f.maxAcres > 0 && plot.areaAcres > f.maxAcres) return@filter false
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

data class StageMeta(val label: String, val icon: String, val color: String, val bg: String, val step: Int)

fun stageMeta(stage: String): StageMeta = when (stage) {
    "SOWN" -> StageMeta("Sown / Germinating", "🌱", "#15803d", "#dcfce7", 1)
    "GROWING" -> StageMeta("Vegetative Growth", "🌿", "#0369a1", "#e0f2fe", 2)
    "FLOWERING" -> StageMeta("Flowering & Fruiting", "🌸", "#b45309", "#fef3c7", 3)
    "HARVEST_READY" -> StageMeta("Harvest Ready", "🌾", "#7e22ce", "#f3e8ff", 4)
    else -> StageMeta(stage, "📍", "#475569", "#f1f5f9", 0)
}

val ALL_STAGES = listOf("SOWN", "GROWING", "FLOWERING", "HARVEST_READY")

/** Land service calls with the seed plots / reservations as an offline fallback. */
object LandRepository {
    private var seedPlots: List<PlotItem> = emptyList()
    /** Reservations created in this session (plus the seeds), used when the service is unreachable. */
    private var localReservations: List<ReservationItem> = emptyList()

    fun init(context: Context) {
        fun read(name: String) = context.assets.open(name).bufferedReader().readText()
        seedPlots = runCatching { Http.json.decodeFromString<List<PlotItem>>(read("seed_plots.json")) }.getOrDefault(emptyList())
        localReservations = runCatching { Http.json.decodeFromString<List<ReservationItem>>(read("seed_reservations.json")) }.getOrDefault(emptyList())
    }

    suspend fun fetchPlots(lat: Double, lng: Double, radiusKm: Int): List<PlotItem> {
        try {
            val data = LandApi.searchPlots(lat, lng, radiusKm)
            if (data.isNotEmpty()) return data.map { p ->
                val c = p.centroid?.coordinates?.takeIf { it.size >= 2 } ?: listOf(lng, lat)
                PlotItem(
                    id = p.id, farmerId = p.farmerId, title = p.title, description = p.description, areaAcres = p.areaAcres,
                    soilType = p.soilType ?: "Loam", waterSource = p.waterSource ?: "Borewell", status = p.status ?: "AVAILABLE",
                    village = "Local Village", district = "Local District", distanceM = haversineM(lat, lng, c[1], c[0]),
                    centroid = c, boundary = p.boundary?.coordinates ?: emptyList(),
                    seasons = p.seasons.map { s -> PlotSeasonItem(s.id, s.plotId, s.season, s.startDate, s.endDate, s.price, s.allowedCrops) },
                )
            }
        } catch (_: Exception) {
            // Graceful fallback to the seed dataset
        }
        return seedPlots.map { it.copy(distanceM = haversineM(lat, lng, it.centroid[1], it.centroid[0])) }
    }

    suspend fun fetchReservations(): List<ReservationItem> {
        try {
            val data = LandApi.getMyReservations()
            if (data.isNotEmpty()) return data.map { r ->
                ReservationItem(
                    id = r.id, plotId = r.plotSeasonId, plotTitle = r.plotTitle ?: "Reserved Farmland Plot",
                    plotSeasonId = r.plotSeasonId, seasonName = r.seasonName ?: "Active Season", farmerId = r.farmerId,
                    farmerName = "Assigned Farmer", cropId = r.cropId, cropName = r.cropId.uppercase(),
                    expectedQtyKg = r.expectedQtyKg, status = r.status, deposit = r.deposit ?: 0.0, total = r.total ?: 0.0,
                    createdAt = r.createdAt ?: LocalDate.now().toString(),
                )
            }
        } catch (_: Exception) {
            // Fallback to local reservations
        }
        return localReservations
    }

    suspend fun submitReservation(plot: PlotItem, season: PlotSeasonItem, cropId: String, qtyKg: Double): ReservationItem {
        val (deposit, total) = reservationEstimate(season.pricePerKg, qtyKg)
        try {
            LandApi.createReservation(ReservationCreate(season.id, cropId, qtyKg))
        } catch (_: Exception) {
            // Simulated offline creation
        }
        val today = LocalDate.now().toString()
        val qtyText = if (qtyKg % 1.0 == 0.0) qtyKg.toLong().toString() else qtyKg.toString()
        val created = ReservationItem(
            id = "res-${System.currentTimeMillis().toString().takeLast(6)}",
            plotId = plot.id, plotTitle = plot.title, plotSeasonId = season.id, seasonName = season.season,
            farmerId = plot.farmerId, farmerName = plot.farmerName, farmerPhone = plot.farmerPhone,
            cropId = cropId, cropName = cropId.replaceFirstChar { it.uppercase() }, expectedQtyKg = qtyKg,
            status = "PENDING", deposit = deposit, total = total, createdAt = today,
            updates = listOf(
                CropTimelineItem(
                    id = "upd-init-${System.currentTimeMillis()}", stage = "SOWN",
                    stageTitle = "Reservation Submitted & Sowing Prep",
                    note = "Reservation requested for $qtyText kg of $cropId. Farmer will accept terms and begin field preparation.",
                    createdAt = today,
                ),
            ),
        )
        localReservations = listOf(created) + localReservations
        return created
    }
}
