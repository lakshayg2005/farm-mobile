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
        organic: Boolean? = null,
        sort: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): ListingFeatureCollection {
        val res = Http.get(
            "/catalog/listings/search",
            linkedMapOf(
                "lat" to lat, "lng" to lng, "radius_km" to radiusKm, "crop" to crop,
                "min_price" to minPrice, "max_price" to maxPrice, "organic" to organic,
                "sort" to sort, "limit" to limit, "cursor" to cursor,
            ),
        )
        if (!res.ok) throw apiError(res, "Could not search produce")
        return res.decode()
    }

    suspend fun getListing(listingId: String): ListingOut {
        val res = Http.get("/catalog/listings/${Http.enc(listingId)}")
        if (!res.ok) throw apiError(res, "Could not load the listing")
        return res.decode()
    }

    /** The crop catalogue (GET /catalog/crops): stable ids such as "tomato" and display names. */
    suspend fun listCrops(): List<CropOut> {
        val res = Http.get("/catalog/crops")
        if (!res.ok) throw apiError(res, "Could not load the crops")
        return res.decode()
    }

    suspend fun getMedia(mediaId: String): MediaOut {
        val res = Http.get("/media/${Http.enc(mediaId)}")
        if (!res.ok) throw apiError(res, "Could not load the photo")
        return res.decode()
    }
}

object LandApi {
    /**
     * Open plot-seasons near a point (or in a map box when [bbox] is west, south, east, north).
     * The reply has one feature per plot-season; [offset] pages through them.
     */
    suspend fun searchPlots(
        lat: Double?,
        lng: Double?,
        radiusKm: Int? = null,
        season: String? = null,
        crop: String? = null,
        limit: Int = 100,
        offset: Int = 0,
        bbox: List<Double>? = null,
    ): PlotSearchResult {
        val res = Http.get(
            "/land/plots/search",
            linkedMapOf(
                "lat" to lat, "lng" to lng, "radius_km" to radiusKm, "season" to season, "crop" to crop,
                "west" to bbox?.getOrNull(0), "south" to bbox?.getOrNull(1),
                "east" to bbox?.getOrNull(2), "north" to bbox?.getOrNull(3),
                "limit" to limit, "offset" to offset,
            ),
        )
        if (!res.ok) throw apiError(res, "Could not search plots")
        return res.decode()
    }

    /** One plot with its description, soil and water (GET /land/plots/{id}). */
    suspend fun getPlot(plotId: String): PlotOut {
        val res = Http.get("/land/plots/${Http.enc(plotId)}")
        if (!res.ok) throw apiError(res, "Could not load the plot")
        return res.decode()
    }

    suspend fun createReservation(body: ReservationCreate): ReservationOut {
        val res = Http.post("/land/reservations", Http.encode(body))
        if (!res.ok) throw apiError(res, "Could not reserve the plot")
        return res.decode()
    }

    suspend fun getMyReservations(): List<ReservationOut> {
        val res = Http.get("/land/reservations/mine")
        if (!res.ok) throw apiError(res, "Could not load your reservations")
        return res.decode()
    }

    /** The farmer's crop updates for a reservation, oldest first. */
    suspend fun getCropUpdates(reservationId: String): List<CropUpdateOut> {
        val res = Http.get("/land/reservations/${Http.enc(reservationId)}/updates")
        if (!res.ok) throw ApiException(res.error()?.message ?: "Could not load the crop updates (${res.status})", res.error()?.code ?: "REQUEST_FAILED", res.status)
        return res.decode()
    }

    /** The agreement made when the farmer accepted, or null before that (404 AGREEMENT_NOT_FOUND). */
    suspend fun getAgreement(reservationId: String): AgreementOut? {
        val res = Http.get("/land/reservations/${Http.enc(reservationId)}/agreement")
        if (res.status == 404) return null
        if (!res.ok) throw ApiException(res.error()?.message ?: "Could not load the agreement (${res.status})", res.error()?.code ?: "REQUEST_FAILED", res.status)
        return res.decode()
    }

    suspend fun cancelReservation(reservationId: String): ReservationOut {
        val res = Http.post("/land/reservations/${Http.enc(reservationId)}/cancel")
        if (!res.ok) throw apiError(res, "Could not cancel the reservation")
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
    private data class StopActionIn(val reason: String? = null, val notes: String? = null)

    @Serializable
    private data class LocationPingIn(
        val lat: Double,
        val lng: Double,
        val poolId: String? = null,
        val speedMs: Double? = null,
        val heading: Double? = null,
        val accuracyM: Double? = null,
        val recordedAt: String,
    )

    /** Marks the partner as arrived at a stop (POST /logistics/stops/{id}/arrive). Throws if refused. */
    suspend fun arrive(stopId: String): Stop = postStop("/stops/${Http.enc(stopId)}/arrive", "{}", "Arrival was not recorded")

    /** Skips a stop that cannot be worked (customer away, farm closed). Throws if refused. */
    suspend fun skip(stopId: String, reason: String?, notes: String? = null): Stop = postStop(
        "/stops/${Http.enc(stopId)}/skip",
        Http.encode(StopActionIn(reason, notes)),
        "The stop was not skipped",
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
        lat: Double, lng: Double, speed: Double?, heading: Double?, accuracy: Double?, recordedAt: String, poolId: String? = null,
    ): Boolean = load {
        Http.post("/logistics/location", Http.encode(LocationPingIn(lat, lng, poolId, speed, heading, accuracy, recordedAt))).ok
    }.getOrDefault(false)

    /** Routes offered to this partner that are still open (nearest offer first from the server). */
    /** Every route of the signed-in partner in the last [days] days, today included, newest first. */
    suspend fun myRoutes(days: Int = 14): List<Pool> {
        val res = Http.get("/logistics/my-routes", mapOf("days" to days.toString()))
        if (!res.ok) throw ApiException(errorMessage(res, "Could not load your route history"), res.error()?.code ?: "REQUEST_FAILED", res.status)
        return res.decode()
    }

    suspend fun myOffers(): List<RouteOffer> {
        val res = Http.get("/logistics/offers/mine")
        if (!res.ok) throw ApiException(errorMessage(res, "Could not load route offers"), res.error()?.code ?: "REQUEST_FAILED", res.status)
        return res.decode()
    }

    /**
     * Takes an offered route. The first partner to accept gets it; a later one gets an
     * [ApiException] with code POOL_TAKEN (409). Returns the route on success.
     */
    suspend fun acceptOffer(poolId: String): Pool {
        val res = Http.post("/logistics/offers/${Http.enc(poolId)}/accept", "{}")
        if (!res.ok) throw ApiException(errorMessage(res, "Could not accept the route"), res.error()?.code ?: "REQUEST_FAILED", res.status)
        return res.decode()
    }

    suspend fun declineOffer(poolId: String) {
        val res = Http.post("/logistics/offers/${Http.enc(poolId)}/decline", "{}")
        if (!res.ok && res.status != 404) throw ApiException(errorMessage(res, "Could not decline the route"), res.error()?.code ?: "REQUEST_FAILED", res.status)
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
        else URL(URL(Http.baseUrlProvider()), signed.uploadUrl).toString()
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

    /** The delivery choices at checkout (immediate or shared) with their fees and the batch time. */
    suspend fun deliveryOptions(): DeliveryOptions {
        val res = Http.get("/order/orders/delivery-options")
        if (!res.ok) throw ApiException("Could not load the delivery options (${res.status})", status = res.status)
        return res.decode()
    }

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

    /** Cancel an order (allowed until it is packed); the server records the refund. Returns the raw result. */
    suspend fun cancelOrder(orderId: String): HttpResult =
        Http.post("/order/orders/${Http.enc(orderId)}/cancel", "{}")

    suspend fun rateOrder(orderId: String, body: OrderRatingCreate): HttpResult =
        Http.post("/order/orders/${Http.enc(orderId)}/rating", Http.encode(body))

    suspend fun chooseHarvest(body: HarvestOrderChoice): HttpResult =
        Http.post("/order/orders/harvest", Http.encode(body))
}

/** Notification service (gateway prefix /notification). */
object NotificationApi {
    @Serializable
    private data class UnreadCount(val count: Int = 0)

    /** A page of the inbox, newest first; [unreadOnly] asks the server for unread ones only. */
    suspend fun list(cursor: String?, limit: Int = 20, unreadOnly: Boolean = false): NotificationPage {
        val res = Http.get(
            "/notification/notifications",
            linkedMapOf("limit" to limit, "cursor" to cursor, "unreadOnly" to if (unreadOnly) true else null),
        )
        if (!res.ok) throw ApiException("Could not load notifications (${res.status})", status = res.status)
        return res.decode()
    }

    /** The number on the bell (GET /notification/notifications/unread-count). */
    suspend fun unreadCount(): Int {
        val res = Http.get("/notification/notifications/unread-count")
        if (!res.ok) throw ApiException("Could not load the unread count (${res.status})", status = res.status)
        return res.decode<UnreadCount>().count
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
