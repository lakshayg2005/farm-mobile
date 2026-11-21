package com.farm.customer.browse

import android.content.Context
import com.farm.core.api.CatalogApi
import com.farm.core.api.Http
import com.farm.core.api.MediaOut
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class ProducePhoto(
    val id: String,
    val url: String = "",
    val thumbUrl: String = "",
    val isGeoVerified: Boolean = false,
    val geoStatus: String = "PENDING",
    val distanceM: Double? = null,
    val rejectReason: String? = null,
)

@Serializable
data class ProduceListing(
    val id: String,
    val farmId: String,
    val farmName: String,
    val farmerId: String = "",
    val farmerName: String? = null,
    val cropId: String,
    val cropName: String,
    val variety: String? = null,
    val unit: String,
    val pricePerUnit: Double,
    val qtyAvailable: Double,
    val harvestDate: String,
    val grade: String? = null,
    val isOrganic: Boolean = false,
    val description: String? = null,
    val distanceM: Double = 0.0,
    val district: String = "",
    val village: String? = null,
    val status: String = "ACTIVE",
    /** [lng, lat] */
    val pickupCoordinates: List<Double> = listOf(0.0, 0.0),
    val mediaIds: List<String> = emptyList(),
    val photos: List<ProducePhoto> = emptyList(),
) {
    val displayName: String get() = cropName + (variety?.let { " ($it)" } ?: "")
}

enum class SortOption(val label: String) {
    DISTANCE("Nearest"), PRICE_ASC("Price: Low to High"), PRICE_DESC("Price: High to Low"), HARVEST_DATE("Freshest Harvest")
}

data class FilterState(
    val query: String = "",
    val crop: String? = null,
    val radiusKm: Int = 15,
    val organicOnly: Boolean = false,
    val minGrade: String = "ALL",
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: SortOption = SortOption.DISTANCE,
)

data class LocationCoords(val name: String, val latitude: Double, val longitude: Double)

val POPULAR_DISTRICTS = listOf(
    LocationCoords("Durg, CG", 21.1904, 81.2849),
    LocationCoords("Raipur, CG", 21.2514, 81.6296),
    LocationCoords("Bilaspur, CG", 22.0797, 82.1409),
    LocationCoords("Nashik, MH", 19.9975, 73.7898),
    LocationCoords("Pune, MH", 18.5204, 73.8567),
    LocationCoords("Jaipur, RJ", 26.9124, 75.7873),
)

fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return Math.round(r * 2 * atan2(sqrt(a), sqrt(1 - a))).toDouble()
}

// ─── Filtering ──────────────────────────────────────────────────────────────

private val GRADE_RANK = mapOf("A" to 3, "B" to 2, "C" to 1)

fun filterListings(listings: List<ProduceListing>, f: FilterState): List<ProduceListing> = listings.filter { item ->
    val q = f.query.trim().lowercase()
    if (q.isNotEmpty()) {
        val hit = item.cropName.lowercase().contains(q) || item.variety?.lowercase()?.contains(q) == true ||
            item.farmName.lowercase().contains(q) || item.district.lowercase().contains(q) ||
            item.village?.lowercase()?.contains(q) == true
        if (!hit) return@filter false
    }
    if (f.crop != null && f.crop != "ALL" && !item.cropId.equals(f.crop, ignoreCase = true)) return@filter false
    if (f.radiusKm > 0 && item.distanceM > f.radiusKm * 1000) return@filter false
    if (f.organicOnly && !item.isOrganic) return@filter false
    if (f.minGrade != "ALL") {
        val g = item.grade ?: return@filter false
        if ((GRADE_RANK[g] ?: 0) < (GRADE_RANK[f.minGrade] ?: 0)) return@filter false
    }
    if (f.minPrice != null && f.minPrice > 0 && item.pricePerUnit < f.minPrice) return@filter false
    if (f.maxPrice != null && f.maxPrice > 0 && item.pricePerUnit > f.maxPrice) return@filter false
    true
}

private fun epochOf(date: String): Long = runCatching { LocalDate.parse(date.take(10)).toEpochDay() }.getOrDefault(0)

fun sortListings(listings: List<ProduceListing>, sort: SortOption): List<ProduceListing> = when (sort) {
    SortOption.DISTANCE -> listings.sortedBy { it.distanceM }
    SortOption.PRICE_ASC -> listings.sortedBy { it.pricePerUnit }
    SortOption.PRICE_DESC -> listings.sortedByDescending { it.pricePerUnit }
    SortOption.HARVEST_DATE -> listings.sortedByDescending { epochOf(it.harvestDate) }
}

// ─── Formatting ─────────────────────────────────────────────────────────────

/** 450 -> "450 m", 2400 -> "2.4 km". */
fun formatDistance(metres: Double): String {
    if (metres < 1000) return "${metres.roundToInt()} m"
    val km = metres / 1000
    return if (km < 10) String.format(Locale.US, "%.1f km", km) else "${km.roundToInt()} km"
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-11-15" -> "15 Nov 2026". */
fun formatHarvestDate(date: String): String = runCatching {
    val d = if (date.length > 10) OffsetDateTime.parse(date).toLocalDate() else LocalDate.parse(date)
    "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
}.getOrDefault(date)

data class GradeMeta(val label: String, val bg: String, val fg: String, val description: String)

fun gradeMeta(grade: String?): GradeMeta = when (grade) {
    "A" -> GradeMeta("Grade A", "#d8f3dc", "#1b4332", "Premium quality - highest size, uniformity and freshness standard")
    "B" -> GradeMeta("Grade B", "#e0e7ff", "#3730a3", "Standard quality - fresh and nutritious with minor cosmetic variation")
    "C" -> GradeMeta("Grade C", "#fef3c7", "#92400e", "Commercial grade - great for cooking, preserves and juices")
    else -> GradeMeta("Ungraded", "#f3f4f6", "#6b7280", "Quality inspection pending")
}

data class PhotoVerification(
    val isVerified: Boolean, val label: String, val shortLabel: String,
    val bg: String, val fg: String, val border: String, val description: String,
)

/** "Photos with verified badge": geo-verified means the photo was taken on the farm. */
fun photoVerification(photo: ProducePhoto?): PhotoVerification {
    if (photo == null) {
        return PhotoVerification(false, "Photo Unverified", "Unverified", "#f3f4f6", "#6b7280", "#d1d5db", "No GPS metadata found in photo")
    }
    if (photo.isGeoVerified) {
        val dist = photo.distanceM?.let { " (${formatDistance(it)} from farm centroid)" } ?: ""
        return PhotoVerification(true, "✓ Verified Farm Photo", "✓ Verified", "#dcfce7", "#166534", "#86efac", "GPS verified: Photo taken at the farm location$dist")
    }
    val desc = when {
        photo.geoStatus == "TOO_FAR" -> "Photo taken too far from farm location" +
            (photo.distanceM?.takeIf { it > 0 }?.let { " (${formatDistance(it)} away)" } ?: "")
        photo.geoStatus == "NO_GPS" -> "Photo lacks GPS EXIF metadata"
        photo.rejectReason != null -> photo.rejectReason
        else -> "Photo could not be verified at farm coordinates"
    }
    return PhotoVerification(false, "⚠ Unverified Photo", "⚠ Unverified", "#fee2e2", "#991b1b", "#fca5a5", desc)
}

fun cropEmoji(cropId: String): String = when (cropId) {
    "tomato" -> "🍅"; "spinach" -> "🥬"; "onion" -> "🧅"; "potato" -> "🥔"; "mango" -> "🥭"; "okra" -> "🌿"; "carrot" -> "🥕"
    else -> "🥕"
}

// ─── Data: catalog service, falling back to the seed listings when it is unreachable ────────

object BrowseRepository {
    private var seeds: List<ProduceListing> = emptyList()

    fun init(context: Context) {
        seeds = runCatching {
            Http.json.decodeFromString<List<ProduceListing>>(context.assets.open("seed_listings.json").bufferedReader().readText())
        }.getOrDefault(emptyList())
    }

    private fun MediaOut.toPhoto() = ProducePhoto(
        id = id, url = url ?: thumbUrl ?: "", thumbUrl = thumbUrl ?: url ?: "",
        isGeoVerified = isGeoVerified, geoStatus = geoStatus, distanceM = distanceM, rejectReason = rejectReason,
    )

    /** Nearby produce (GET /catalog/listings/search), or the seeds with distances from [lat],[lng]. */
    suspend fun fetchNearby(lat: Double, lng: Double, radiusKm: Int, crop: String?, organic: Boolean?): List<ProduceListing> {
        try {
            val geo = CatalogApi.searchListings(lat, lng, radiusKm = radiusKm, crop = crop, organic = organic)
            return geo.features.map { f ->
                val p = f.properties
                val coords = f.geometry?.coordinates?.takeIf { it.size >= 2 } ?: listOf(lng, lat)
                val photos = p.mediaIds.firstOrNull()?.let { id -> runCatching { listOf(CatalogApi.getMedia(id).toPhoto()) }.getOrNull() } ?: emptyList()
                ProduceListing(
                    id = p.id, farmId = p.farmId, farmName = p.farmName, farmerId = p.farmerId, cropId = p.cropId,
                    cropName = p.cropName, variety = p.variety, unit = p.unit, pricePerUnit = p.pricePerUnit,
                    qtyAvailable = p.qtyAvailable, harvestDate = p.harvestDate, grade = p.grade, isOrganic = p.isOrganic,
                    description = p.description, distanceM = p.distanceM, district = p.district, village = p.village,
                    status = p.status, pickupCoordinates = coords, mediaIds = p.mediaIds, photos = photos,
                )
            }
        } catch (_: Exception) {
            // Graceful fallback to the seed dataset
        }
        return seeds.map { s -> s.copy(distanceM = haversineM(lat, lng, s.pickupCoordinates[1], s.pickupCoordinates[0])) }
    }

    /** One listing (GET /catalog/listings/{id}), or the matching seed. */
    suspend fun fetchDetail(listingId: String): ProduceListing {
        try {
            val out = CatalogApi.getListing(listingId)
            val photos = out.mediaIds.mapNotNull { id -> runCatching { CatalogApi.getMedia(id).toPhoto() }.getOrNull() }
            return ProduceListing(
                id = out.id, farmId = out.farmId, farmName = out.farmName, farmerId = out.farmerId, cropId = out.cropId,
                cropName = out.cropName, variety = out.variety, unit = out.unit, pricePerUnit = out.pricePerUnit,
                qtyAvailable = out.qtyAvailable, harvestDate = out.harvestDate, grade = out.grade, isOrganic = out.isOrganic,
                description = out.description, distanceM = 0.0, district = out.district, village = out.village,
                status = out.status, pickupCoordinates = out.pickupLocation?.coordinates ?: listOf(0.0, 0.0),
                mediaIds = out.mediaIds, photos = photos,
            )
        } catch (_: Exception) {
            // Fallback to the seed listing
        }
        return seeds.find { it.id == listingId } ?: throw IllegalStateException("Listing $listingId not found")
    }
}
