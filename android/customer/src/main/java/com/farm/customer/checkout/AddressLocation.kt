package com.farm.customer.checkout

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** What the phone's position turned into: always the point, and the address parts when known. */
data class FoundAddress(
    val lat: Double,
    val lng: Double,
    val line1: String? = null,
    val area: String? = null,
    val city: String? = null,
    val pincode: String? = null,
)

/**
 * The form with the phone's position: the coordinates always, and street, area, city and pincode
 * where the lookup found them (what it did not find is left as the person typed it).
 */
fun AddressForm.withLocation(found: FoundAddress): AddressForm = copy(
    lat = String.format(Locale.US, "%.6f", found.lat),
    lng = String.format(Locale.US, "%.6f", found.lng),
    line1 = found.line1?.takeIf { it.isNotBlank() } ?: line1,
    line2 = found.area?.takeIf { it.isNotBlank() } ?: line2,
    city = found.city?.takeIf { it.isNotBlank() } ?: city,
    pincode = found.pincode?.filter(Char::isDigit)?.takeIf { it.length == 6 } ?: pincode,
)

/** The form fields that differ between [a] and [b] (to clear only their errors). */
fun changedFields(a: AddressForm, b: AddressForm): Set<String> = buildSet {
    if (a.line1 != b.line1) add("line1")
    if (a.line2 != b.line2) add("line2")
    if (a.city != b.city) add("city")
    if (a.pincode != b.pincode) add("pincode")
    if (a.contactPhone != b.contactPhone) add("contactPhone")
    if (a.lat != b.lat) add("lat")
    if (a.lng != b.lng) add("lng")
}

/** Street line from a geocoder result: "12 MG Road", else the first part of the full line. */
internal fun streetOf(a: Address): String? {
    val street = listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").trim()
    if (street.isNotEmpty()) return street
    a.featureName?.takeIf { it.isNotBlank() && it != a.postalCode && it != a.subThoroughfare }?.let { return it }
    return a.getAddressLine(0)?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Turns a point into an address with the phone's geocoder (needs a network on most phones).
 * Without a geocoder, or when the lookup fails, only the point is returned.
 */
suspend fun lookUpAddress(context: Context, lat: Double, lng: Double): FoundAddress {
    val plain = FoundAddress(lat, lng)
    if (!Geocoder.isPresent()) return plain
    val geocoder = Geocoder(context, Locale("en", "IN"))
    val address: Address? = withTimeoutOrNull(8_000) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    lat, lng, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }
    address ?: return plain
    return FoundAddress(
        lat = lat,
        lng = lng,
        line1 = streetOf(address),
        area = address.subLocality,
        city = address.locality ?: address.subAdminArea,
        pincode = address.postalCode,
    )
}
