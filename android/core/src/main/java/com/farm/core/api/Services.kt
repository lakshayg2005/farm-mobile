package com.farm.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.time.Instant

// Ports of packages/api/src/{catalog,land,logistics,media,order,notification}.ts.
// Every path is relative to the gateway base URL (…/api/v1).

object CatalogApi {
    /** Search produce listings near coordinates (GET /catalog/listings/search). */
    suspend fun searchListings(
        lat: Double,
        lng: Double,
        radiusKm: Int? = null,
        crop: String? = null,
        minPrice: Double? = null,
        maxPrice: Double? = null,
        grade: String? = null,
        organic: Boolean? = null,
        sort: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): ListingFeatureCollection {
        val res = Http.get(
            "/catalog/listings/search",
            linkedMapOf(
                "lat" to lat, "lng" to lng, "radius_km" to radiusKm, "crop" to crop,
                "min_price" to minPrice, "max_price" to maxPrice, "grade" to grade, "organic" to organic,
                "sort" to sort, "limit" to limit, "cursor" to cursor,
            ),
        )
        if (!res.ok) throw ApiException("Failed to search listings: ${res.status}", status = res.status)
        return res.decode()
    }

    suspend fun getListing(listingId: String): ListingOut {
        val res = Http.get("/catalog/listings/${Http.enc(listingId)}")
        if (!res.ok) throw ApiException("Failed to get listing $listingId: ${res.status}", status = res.status)
        return res.decode()
    }

    suspend fun getMedia(mediaId: String): MediaOut {
        val res = Http.get("/media/${Http.enc(mediaId)}")
        if (!res.ok) throw ApiException("Failed to get media $mediaId: ${res.status}", status = res.status)
        return res.decode()
    }
}

object LandApi {
    suspend fun searchPlots(lat: Double?, lng: Double?, radiusKm: Int? = null, season: String? = null, crop: String? = null): List<PlotOut> {
        val res = Http.get(
            "/land/plots/search",
            linkedMapOf("lat" to lat, "lng" to lng, "radius_km" to radiusKm, "season" to season, "crop" to crop),
        )
        if (!res.ok) throw ApiException("Failed to search plots: ${res.status}", status = res.status)
        return res.decode()
    }

    suspend fun createReservation(body: ReservationCreate): ReservationOut {
        val res = Http.post("/land/reservations", Http.encode(body))
        if (!res.ok) throw ApiException("Failed to create reservation: ${res.status}", status = res.status)
        return res.decode()
    }

    suspend fun getMyReservations(): List<ReservationOut> {
        val res = Http.get("/land/reservations/mine")
        if (!res.ok) throw ApiException("Failed to get my reservations: ${res.status}", status = res.status)
        return res.decode()
    }

    suspend fun cancelReservation(reservationId: String): ReservationOut {
        val res = Http.post("/land/reservations/${Http.enc(reservationId)}/cancel")
        if (!res.ok) throw ApiException("Failed to cancel reservation: ${res.status}", status = res.status)
        return res.decode()
    }
}

object LogisticsApi {
    /** The platform's error body `{ error: { code, message, details } }`, falling back to the HTTP status. */
    private fun errorMessage(res: HttpResult, fallback: String): String {
        val error = res.error()
        val detail = runCatching { (error?.details as? JsonArray)?.firstOrNull() as? JsonObject }.getOrNull()
        val detailMsg = detail?.get("message")?.jsonPrimitive?.content
        val field = detail?.get("field")?.jsonPrimitive?.content
        if (error?.message != null && detailMsg != null) {
            return "${error.message}: ${if (field != null) "$field " else ""}$detailMsg"
        }
        if (error?.message != null) return error.message
        return "$fallback (${res.status})"
    }

    private suspend fun postStop(path: String, body: String, fallback: String): Stop {
        val res = Http.post("/logistics$path", body)
        if (!res.ok) throw ApiException(errorMessage(res, fallback), status = res.status)
        return res.decode()
    }

    @Serializable
    private data class PickupIn(val notes: String? = null, val collectedAt: String)

    @Serializable
    private data class DeliverIn(val otp: String, val photoMediaId: String, val notes: String? = null, val deliveredAt: String)

    @Serializable
    private data class LocationPingIn(
        val lat: Double,
        val lng: Double,
        val speedMs: Double? = null,
        val heading: Double? = null,
        val accuracyM: Double? = null,
        val recordedAt: String,
    )

    /** Confirms pickup at a farm stop. Throws with the server's message if refused. */
    suspend fun submitPickup(stopId: String, notes: String?): Stop = postStop(
        "/stops/${Http.enc(stopId)}/pickup",
        Http.encode(PickupIn(notes, Instant.now().toString())),
        "Pickup was not recorded",
    )

    /** Confirms delivery with the customer's OTP and the proof photo. Throws if refused. */
    suspend fun submitDelivery(stopId: String, otp: String, photoMediaId: String, notes: String?): Stop = postStop(
        "/stops/${Http.enc(stopId)}/deliver",
        Http.encode(DeliverIn(otp, photoMediaId, notes, Instant.now().toString())),
        "Delivery was not recorded",
    )

    /** The signed-in partner's pool for a day (YYYY-MM-DD), or null when none is assigned. */
    suspend fun getMyRoute(poolDate: String?): Pool? {
        val res = Http.get("/logistics/my-route", mapOf("pool_date" to poolDate))
        if (res.status == 404) return null
        if (!res.ok) throw ApiException(errorMessage(res, "Could not load your route"), status = res.status)
        return res.decode()
    }

    /** Sends one GPS ping. Never throws: returns false when offline or refused. */
    suspend fun sendLocationPing(
        lat: Double, lng: Double, speed: Double?, heading: Double?, accuracy: Double?, recordedAt: String,
    ): Boolean = try {
        Http.post("/logistics/location", Http.encode(LocationPingIn(lat, lng, speed, heading, accuracy, recordedAt))).ok
    } catch (_: Exception) {
        false
    }

    /** Live position of an order's delivery. null while the order is not in a pool yet. */
    suspend fun track(orderId: String): Track? {
        val res = Http.get("/logistics/orders/${Http.enc(orderId)}/track")
        if (res.status == 404) return null
        if (!res.ok) throw ApiException("Could not load the delivery position", status = res.status)
        return res.decode()
    }
}

object MediaApi {
    @Serializable
    private data class UploadUrlIn(val ownerType: String, val ownerId: String, val contentType: String, val sizeBytes: Long)

    @Serializable
    private data class UploadUrlOut(val mediaId: String, val uploadUrl: String, val headers: Map<String, String> = emptyMap())

    @Serializable
    private data class CompleteOut(val id: String? = null, val url: String? = null)

    private fun mediaError(res: HttpResult, step: String): Exception =
        ApiException(res.error()?.message ?: "Photo upload failed at $step (${res.status})", status = res.status)

    /**
     * Uploads a proof-of-delivery photo in the media service's 3 steps:
     * 1. POST /media/upload-url -> signed uploadUrl + mediaId (signed for this exact size and type)
     * 2. PUT the bytes to uploadUrl
     * 3. POST /media/{mediaId}/complete
     * Returns the media id. Throws if any step fails.
     */
    suspend fun uploadDeliveryPhoto(bytes: ByteArray, mimeType: String, stopId: String): String {
        val type = if (mimeType in listOf("image/jpeg", "image/png", "image/webp")) mimeType else "image/jpeg"
        val urlRes = Http.post(
            "/media/upload-url",
            Http.encode(UploadUrlIn("DELIVERY", stopId, type, bytes.size.toLong())),
        )
        if (!urlRes.ok) throw mediaError(urlRes, "upload-url")
        val signed = urlRes.decode<UploadUrlOut>()

        val target = if (signed.uploadUrl.startsWith("http")) signed.uploadUrl
        else URL(URL(ApiConfig.baseUrl), signed.uploadUrl).toString()
        val put = Http.call(
            "PUT", target,
            headers = mapOf("Content-Type" to type) + signed.headers,
            token = null, absolute = true,
            rawBody = bytes.toRequestBody(type.toMediaType()),
        )
        if (!put.ok) throw ApiException("Photo upload failed (${put.status})", status = put.status)

        val done = Http.post("/media/${Http.enc(signed.mediaId)}/complete", "{}")
        if (!done.ok) throw mediaError(done, "complete")
        return runCatching { done.decode<CompleteOut>().id }.getOrNull() ?: signed.mediaId
    }
}

/** Order service (gateway prefix /order). Returns raw results where the caller maps error codes. */
object OrderApi {
    suspend fun createPaymentIntent(body: PaymentIntentCreate): HttpResult =
        Http.post("/order/orders/payments/intent", Http.encode(body))

    suspend fun placeOrder(idempotencyKey: String, body: CheckoutRequest): HttpResult =
        Http.post("/order/orders", Http.encode(body), mapOf("Idempotency-Key" to idempotencyKey))

    suspend fun listOrders(cursor: String?, limit: Int = 20): OrderList {
        val res = Http.get("/order/orders", linkedMapOf("role" to "customer", "limit" to limit, "cursor" to cursor))
        if (!res.ok) throw ApiException("Could not load your orders (${res.status})", status = res.status)
        return res.decode()
    }

    suspend fun getOrder(orderId: String): OrderDetail {
        val res = Http.get("/order/orders/${Http.enc(orderId)}")
        if (!res.ok) {
            throw ApiException(if (res.status == 404) "Order not found" else "Could not load the order", status = res.status)
        }
        return res.decode()
    }

    suspend fun rateOrder(orderId: String, body: OrderRatingCreate): HttpResult =
        Http.post("/order/orders/${Http.enc(orderId)}/rating", Http.encode(body))

    suspend fun chooseHarvest(body: HarvestOrderChoice): HttpResult =
        Http.post("/order/orders/harvest", Http.encode(body))
}

/** Notification service (gateway prefix /notification). */
object NotificationApi {
    suspend fun list(cursor: String?, limit: Int = 20): NotificationPage {
        val res = Http.get("/notification/notifications", linkedMapOf("limit" to limit, "cursor" to cursor))
        if (!res.ok) throw ApiException("Could not load notifications (${res.status})", status = res.status)
        return res.decode()
    }

    suspend fun markRead(id: String) {
        val res = Http.post("/notification/notifications/${Http.enc(id)}/read")
        if (!res.ok) throw ApiException("Could not mark the notification as read", status = res.status)
    }

    suspend fun markAllRead() {
        val res = Http.post("/notification/notifications/read-all")
        if (!res.ok) throw ApiException("Could not mark notifications as read", status = res.status)
    }
}
