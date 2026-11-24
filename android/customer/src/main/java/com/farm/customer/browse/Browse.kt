package com.farm.customer.browse

import com.farm.core.api.CatalogApi
import com.farm.core.api.ListingFeature
import com.farm.core.api.MediaOut
import com.farm.core.api.load
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val farmRating: Double? = null,
    val farmRatingCount: Int = 0,
) {
    val displayName: String get() = cropName + (variety?.let { " ($it)" } ?: "")
}

/** The catalog's search orders ("distance", "price" low to high, "newest"). */
enum class SortOption(val label: String, val api: String) {
    DISTANCE("Nearest", "distance"), PRICE_ASC("Price: low to high", "price"), NEWEST("Newest", "newest")
}

/**
 * Everything except [query] is sent to the catalog search; [query] only narrows the loaded page on
 * the device (the API has no free-text search).
 */
data class FilterState(
    val query: String = "",
    val crop: String? = null,
    val radiusKm: Int = 25,
    val organicOnly: Boolean = false,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: SortOption = SortOption.DISTANCE,
    /** Only farms marked as favourites on this phone (filtered on the device). */
    val favouritesOnly: Boolean = false,
)

/** The catalog rejects a radius above 100 km (422) and needs more than 0. */
fun clampRadius(km: Int): Int = km.coerceIn(1, 100)

/** Listings per search page (the server allows up to 50). */
const val LISTING_PAGE_SIZE = 20

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

/** The text box narrows the loaded results by crop, variety, farm or place. */
fun matchesQuery(item: ProduceListing, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return item.cropName.lowercase().contains(q) || item.variety?.lowercase()?.contains(q) == true ||
        item.farmName.lowercase().contains(q) || item.district.lowercase().contains(q) ||
        item.village?.lowercase()?.contains(q) == true
}

/** True when a change to [next] needs a new search (anything but the text query). */
fun needsRefetch(prev: FilterState, next: FilterState): Boolean =
    prev.copy(query = "", favouritesOnly = false) != next.copy(query = "", favouritesOnly = false)

/** The device-side filters: the text box and "favourite farms only". */
fun applyLocalFilters(items: List<ProduceListing>, f: FilterState, favouriteFarmIds: Set<String>): List<ProduceListing> =
    narrowByQuery(items, f.query).filter { !f.favouritesOnly || it.farmId in favouriteFarmIds }

// ─── Formatting ─────────────────────────────────────────────────────────────

/** 450 -> "450 m", 2400 -> "2.4 km". */
fun formatDistance(metres: Double): String {
    if (metres < 1000) return "${metres.roundToInt()} m"
    val km = metres / 1000
    return if (km < 10) String.format(Locale.US, "%.1f km", km) else "${km.roundToInt()} km"
}

/** 4.25 -> "4.3", 4.0 -> "4.0"; null when not rated yet. */
fun formatRating(rating: Double?): String? = rating?.let { String.format(Locale.US, "%.1f", it) }

/** "★ 4.3 (12)", or "Not rated yet" for a farm with no rating. */
fun ratingLabel(rating: Double?, count: Int): String =
    formatRating(rating)?.let { "★ $it ($count)" } ?: "Not rated yet"

/** Five stars, filled to the nearest whole star: "★★★★☆". */
fun ratingStars(rating: Double): String {
    val full = rating.roundToInt().coerceIn(0, 5)
    return "★".repeat(full) + "☆".repeat(5 - full)
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-11-15" -> "15 Nov 2026". */
fun formatHarvestDate(date: String): String = runCatching {
    val d = if (date.length > 10) OffsetDateTime.parse(date).toLocalDate() else LocalDate.parse(date)
    "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
}.getOrDefault(date)

data class PhotoVerification(
    val isVerified: Boolean, val label: String, val shortLabel: String,
    val bg: String, val fg: String, val border: String, val description: String,
    /** Still being checked: neither verified nor unverified yet. */
    val pending: Boolean = false,
)

/**
 * The badge of a listing photo. The media service reads the camera's GPS point from the photo and
 * calls it verified only when that point lies within 2 km of the farm. A photo with no location
 * (a screenshot, an edited or a downloaded image) or taken somewhere else is unverified.
 * Null for a listing without a photo: there is nothing to verify.
 */
fun photoVerification(photo: ProducePhoto?): PhotoVerification? {
    if (photo == null) return null
    if (photo.geoStatus == "PENDING" && !photo.isGeoVerified) {
        return PhotoVerification(false, "Checking photo", "Checking", "#E6E9EB", "#4B5258", "#C9CFD4", "Checking where this photo was taken.", pending = true)
    }
    if (photo.isGeoVerified) {
        val dist = photo.distanceM?.takeIf { it > 0 }?.let { ", ${formatDistance(it)} from the farm" } ?: ""
        return PhotoVerification(
            true, "Verified Farm Photo", "Verified", "#E3F1E9", "#155C3A", "#1D7A4D",
            "Taken at the farm: the camera's location matches the farm$dist.",
        )
    }
    val desc = when (photo.geoStatus) {
        "TOO_FAR" -> photo.distanceM?.takeIf { it > 0 }?.let { "Taken ${formatDistance(it)} from the farm, not at it." }
            ?: "Taken away from the farm."
        "NO_GPS" -> "This photo has no camera location (for example a screenshot, an edited or a downloaded image), so it cannot be matched to the farm."
        "NO_REFERENCE" -> "The farm's location is not set, so this photo could not be checked."
        else -> photo.rejectReason ?: "Where this photo was taken could not be checked."
    }
    return PhotoVerification(false, "Unverified Photo", "Unverified", "#FBEAE7", "#C2412D", "#C2412D", desc)
}

// ─── Data: the catalog service only. A failed call throws; nothing is invented. ─────────────

/** One page of search results and the cursor of the next page (null on the last one). */
data class ListingPage(val listings: List<ProduceListing>, val nextCursor: String?)

/** A search hit as a listing. GeoJSON coordinates are [lng, lat]; a hit without a point keeps [fallback]. */
fun ListingFeature.toListing(fallback: List<Double>): ProduceListing {
    val p = properties
    val coords = geometry?.coordinates?.takeIf { it.size >= 2 } ?: fallback
    return ProduceListing(
        id = p.id, farmId = p.farmId, farmName = p.farmName, farmerId = p.farmerId, cropId = p.cropId,
        cropName = p.cropName, variety = p.variety, unit = p.unit, pricePerUnit = p.pricePerUnit,
        qtyAvailable = p.qtyAvailable, harvestDate = p.harvestDate, isOrganic = p.isOrganic,
        description = p.description, distanceM = p.distanceM, district = p.district, village = p.village,
        status = p.status, pickupCoordinates = coords, mediaIds = p.mediaIds,
        farmRating = p.farmRating, farmRatingCount = p.farmRatingCount,
    )
}

object BrowseRepository {
    private fun MediaOut.toPhoto() = ProducePhoto(
        id = id, url = url ?: thumbUrl ?: "", thumbUrl = thumbUrl ?: url ?: "",
        isGeoVerified = isGeoVerified, geoStatus = geoStatus, distanceM = distanceM, rejectReason = rejectReason,
    )

    /**
     * One page of nearby produce (GET /catalog/listings/search). Throws
     * [com.farm.core.api.NetworkException] or [com.farm.core.api.ApiException] on failure. The
     * cover photo of each hit is loaded alongside; one that cannot be loaded is left out.
     */
    suspend fun search(lat: Double, lng: Double, f: FilterState, cursor: String? = null): ListingPage = coroutineScope {
        val geo = CatalogApi.searchListings(
            lat, lng,
            radiusKm = clampRadius(f.radiusKm),
            crop = f.crop?.takeIf { it != "ALL" },
            minPrice = f.minPrice?.takeIf { it > 0 },
            maxPrice = f.maxPrice?.takeIf { it > 0 },
            organic = if (f.organicOnly) true else null,
            sort = f.sort.api,
            limit = LISTING_PAGE_SIZE,
            cursor = cursor,
        )
        val listings = geo.features.map { feature ->
            async {
                val listing = feature.toListing(listOf(lng, lat))
                val cover = listing.mediaIds.firstOrNull()?.let { id -> load { CatalogApi.getMedia(id).toPhoto() }.getOrNull() }
                listing.copy(photos = listOfNotNull(cover))
            }
        }.awaitAll()
        ListingPage(listings, geo.nextCursor)
    }

    /** One listing (GET /catalog/listings/{id}) with all its photos. Throws on failure. */
    suspend fun fetchDetail(listingId: String): ProduceListing = coroutineScope {
        val out = CatalogApi.getListing(listingId)
        val photos = out.mediaIds.map { id -> async { load { CatalogApi.getMedia(id).toPhoto() }.getOrNull() } }.awaitAll().filterNotNull()
        ProduceListing(
            id = out.id, farmId = out.farmId, farmName = out.farmName, farmerId = out.farmerId, cropId = out.cropId,
            cropName = out.cropName, variety = out.variety, unit = out.unit, pricePerUnit = out.pricePerUnit,
            qtyAvailable = out.qtyAvailable, harvestDate = out.harvestDate, isOrganic = out.isOrganic,
            description = out.description, distanceM = 0.0, district = out.district, village = out.village,
            status = out.status, pickupCoordinates = out.pickupLocation?.coordinates ?: listOf(0.0, 0.0),
            mediaIds = out.mediaIds, photos = photos, farmRating = out.farmRating, farmRatingCount = out.farmRatingCount,
        )
    }
}
