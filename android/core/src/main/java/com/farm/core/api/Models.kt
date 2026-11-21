package com.farm.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Statuses and enums are kept as plain strings so a new value from the backend never crashes the app.

// ─── Identity ────────────────────────────────────────────────────────────────

object Roles {
    const val CUSTOMER = "customer"
    const val FARMER = "farmer"
    const val DELIVERY = "delivery"
    const val INSPECTOR = "inspector"
    const val ADMIN = "admin"
}

@Serializable
data class User(
    val id: String,
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val phoneVerified: Boolean = false,
    val farmerVerified: Boolean = false,
    val homeLat: Double? = null,
    val homeLng: Double? = null,
    val createdAt: String = "",
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "bearer",
    val expiresIn: Long = 0,
    val user: User,
)

@Serializable
data class OtpRequestResponse(
    val expiresIn: Int = 0,
    val resendAfter: Int = 0,
    val devCode: String? = null,
)

// ─── Shared geometry ─────────────────────────────────────────────────────────

/** GeoJSON point: coordinates are [longitude, latitude]. */
@Serializable
data class GeoPoint(val type: String = "Point", val coordinates: List<Double> = emptyList()) {
    val lng: Double get() = coordinates.getOrElse(0) { 0.0 }
    val lat: Double get() = coordinates.getOrElse(1) { 0.0 }

    companion object {
        fun of(lat: Double, lng: Double) = GeoPoint("Point", listOf(lng, lat))
    }
}

@Serializable
data class PolygonGeometry(val type: String = "Polygon", val coordinates: List<List<List<Double>>> = emptyList())

// ─── Catalog & media ─────────────────────────────────────────────────────────

@Serializable
data class ListingSearchProperties(
    val id: String,
    val farmId: String = "",
    val farmName: String = "",
    val farmerId: String = "",
    val cropId: String = "",
    val cropName: String = "",
    val variety: String? = null,
    val unit: String = "kg",
    val pricePerUnit: Double = 0.0,
    val qtyAvailable: Double = 0.0,
    val harvestDate: String = "",
    val grade: String? = null,
    val isOrganic: Boolean = false,
    val description: String? = null,
    val distanceM: Double = 0.0,
    val district: String = "",
    val village: String? = null,
    val status: String = "ACTIVE",
    val mediaIds: List<String> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ListingFeature(val geometry: GeoPoint? = null, val properties: ListingSearchProperties)

@Serializable
data class ListingFeatureCollection(val features: List<ListingFeature> = emptyList(), val cursor: String? = null)

@Serializable
data class ListingOut(
    val id: String,
    val farmId: String = "",
    val farmName: String = "",
    val farmerId: String = "",
    val cropId: String = "",
    val cropName: String = "",
    val variety: String? = null,
    val unit: String = "kg",
    val pricePerUnit: Double = 0.0,
    val qtyAvailable: Double = 0.0,
    val harvestDate: String = "",
    val grade: String? = null,
    val isOrganic: Boolean = false,
    val description: String? = null,
    val district: String = "",
    val village: String? = null,
    val pickupLocation: GeoPoint? = null,
    val status: String = "ACTIVE",
    val mediaIds: List<String> = emptyList(),
)

@Serializable
data class MediaOut(
    val id: String,
    val status: String = "",
    val contentType: String = "",
    val url: String? = null,
    val thumbUrl: String? = null,
    val isGeoVerified: Boolean = false,
    val geoStatus: String = "PENDING",
    val distanceM: Double? = null,
    val rejectReason: String? = null,
)

// ─── Land ────────────────────────────────────────────────────────────────────

@Serializable
data class SeasonOut(
    val id: String,
    val plotId: String = "",
    val season: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val price: Double = 0.0,
    val allowedCrops: List<String> = emptyList(),
)

@Serializable
data class PlotOut(
    val id: String,
    val farmerId: String = "",
    val title: String = "",
    val description: String? = null,
    val areaAcres: Double = 0.0,
    val soilType: String? = null,
    val waterSource: String? = null,
    val status: String? = null,
    val boundary: PolygonGeometry? = null,
    val centroid: GeoPoint? = null,
    val seasons: List<SeasonOut> = emptyList(),
)

@Serializable
data class ReservationCreate(val plotSeasonId: String, val cropId: String, val expectedQtyKg: Double)

@Serializable
data class ReservationOut(
    val id: String,
    val plotSeasonId: String = "",
    val farmerId: String = "",
    val customerId: String = "",
    val cropId: String = "",
    val expectedQtyKg: Double = 0.0,
    val status: String = "PENDING",
    val deposit: Double? = null,
    val total: Double? = null,
    val createdAt: String? = null,
    val plotTitle: String? = null,
    val seasonName: String? = null,
)

// ─── Logistics ───────────────────────────────────────────────────────────────

@Serializable
data class Stop(
    val id: String,
    val poolId: String = "",
    val orderId: String = "",
    val seq: Int = 0,
    val status: String = "PENDING", // PENDING, ARRIVED, DONE, SKIPPED
    val stopType: String = "DROP", // PICKUP, DROP
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val doneAt: String? = null,
    val eta: String? = null,
    val notes: String? = null,
    val otp: String? = null,
    val otpVerified: Boolean = false,
    val photoMediaId: String? = null,
    val weightKg: Double? = null,
)

@Serializable
data class Pool(
    val id: String,
    val poolDate: String = "",
    val status: String = "PENDING",
    val partnerId: String? = null,
    val assignedAt: String? = null,
    val createdAt: String = "",
    val kmSaved: Double? = null,
    val totalKm: Double? = null,
    val stops: List<Stop> = emptyList(),
)

@Serializable
data class TrackStop(val lat: Double, val lng: Double, val address: String? = null)

/** GET /logistics/orders/{id}/track. Everything but orderId can be missing. */
@Serializable
data class Track(
    val orderId: String = "",
    val poolId: String? = null,
    val status: String? = null,
    val stop: TrackStop? = null,
    val partnerName: String? = null,
    val partnerPhone: String? = null,
    val partnerLat: Double? = null,
    val partnerLng: Double? = null,
    val partnerUpdatedAt: String? = null,
    val eta: String? = null,
)

// ─── Order ───────────────────────────────────────────────────────────────────

@Serializable
data class DeliveryAddress(
    val label: String? = null,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val pincode: String,
    val location: GeoPoint,
    val contactPhone: String? = null,
)

@Serializable
data class OrderItemIn(val listingId: String, val qty: Double)

@Serializable
data class CheckoutRequest(
    val items: List<OrderItemIn>,
    val deliveryAddress: DeliveryAddress,
    val deliveryDate: String? = null,
    val paymentIntentId: String,
    val notes: String? = null,
)

@Serializable
data class OrderItem(
    val listingId: String? = null,
    val farmId: String? = null,
    val name: String = "",
    val unit: String = "",
    val qty: Double = 0.0,
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0,
    val pickupLocation: GeoPoint? = null,
)

@Serializable
data class Order(
    val id: String,
    val checkoutId: String = "",
    val orderType: String = "PRODUCE",
    val farmId: String? = null,
    val reservationId: String? = null,
    val harvestOption: String? = null,
    val status: String = "PLACED",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryDate: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class Checkout(
    val id: String,
    val customerId: String = "",
    val paymentIntentId: String = "",
    val paymentStatus: String = "",
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val orders: List<Order> = emptyList(),
    val createdAt: String = "",
)

@Serializable
data class OrderEvent(val status: String, val occurredAt: String, val note: String? = null)

@Serializable
data class OrderRating(val orderId: String = "", val score: Int, val comment: String? = null, val createdAt: String = "")

@Serializable
data class PaymentRecord(
    val id: String = "",
    val kind: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val status: String = "",
    val createdAt: String = "",
)

@Serializable
data class OrderDetail(
    val id: String,
    val checkoutId: String = "",
    val orderType: String = "PRODUCE",
    val farmId: String? = null,
    val reservationId: String? = null,
    val harvestOption: String? = null,
    val status: String = "PLACED",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryDate: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val events: List<OrderEvent> = emptyList(),
    val rating: OrderRating? = null,
    val payments: List<PaymentRecord> = emptyList(),
    val balanceDue: Double = 0.0,
)

@Serializable
data class OrderSummary(
    val id: String,
    val checkoutId: String = "",
    val orderType: String = "PRODUCE",
    val farmId: String? = null,
    val reservationId: String? = null,
    val harvestOption: String? = null,
    val status: String = "PLACED",
    val itemCount: Int = 0,
    val total: Double = 0.0,
    val currency: String = "INR",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class OrderList(val items: List<OrderSummary> = emptyList(), val nextCursor: String? = null)

@Serializable
data class PaymentIntentCreate(val amount: Double, val kind: String = "FULL", val referenceId: String? = null)

@Serializable
data class PaymentIntent(
    val id: String,
    val amount: Double = 0.0,
    val currency: String = "INR",
    val kind: String = "FULL",
    val referenceId: String? = null,
    val status: String = "",
    val createdAt: String = "",
)

@Serializable
data class HarvestOrderChoice(
    val reservationId: String,
    val option: String, // DELIVERY or BUYBACK
    val deliveryAddress: DeliveryAddress? = null,
    val deliveryDate: String? = null,
)

@Serializable
data class OrderRatingCreate(val score: Int, val comment: String? = null)

// ─── Notification ────────────────────────────────────────────────────────────

@Serializable
data class AppNotification(
    val id: String,
    val userId: String = "",
    val type: String = "SYSTEM",
    val title: String = "",
    val body: String = "",
    val payload: JsonObject? = null,
    val readAt: String? = null,
    val createdAt: String = "",
)

@Serializable
data class NotificationPage(
    val items: List<AppNotification> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Int = 0,
)
