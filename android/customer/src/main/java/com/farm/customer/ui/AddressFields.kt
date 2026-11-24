package com.farm.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmColors
import com.farm.core.ui.FarmInput
import com.farm.core.ui.TextVariant
import com.farm.customer.browse.NearMeResult
import com.farm.customer.browse.rememberNearMe
import com.farm.customer.checkout.AddressForm
import com.farm.customer.checkout.lookUpAddress
import com.farm.customer.checkout.withLocation
import kotlinx.coroutines.launch

/**
 * The delivery address inputs used by checkout and by a harvest delivery: "Use my current
 * location" fills the street, area, city, pincode and the exact delivery pin from the phone; the
 * person can then correct any line. The pin's coordinates are shown as one line and can still be
 * typed by hand under "Edit".
 */
@Composable
fun DeliveryAddressFields(form: AddressForm, errors: Map<String, String>, onChange: (AddressForm) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var located by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var editPin by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(form)

    val locate = rememberNearMe(auto = false, precise = true) { result ->
        when (result) {
            is NearMeResult.Found -> scope.launch {
                note = "Finding the address…"
                val found = lookUpAddress(context, result.place.latitude, result.place.longitude)
                onChange(latest.withLocation(found))
                located = true
                locating = false
                note = if (found.line1 == null && found.city == null) {
                    "Location pinned. We could not read the street name, so type it below."
                } else {
                    "Address filled from your location. Check it and add a house or flat number."
                }
            }
            NearMeResult.Denied -> { locating = false; note = "Location permission is off. Allow it, or type the address." }
            NearMeResult.Unavailable -> { locating = false; note = "Could not get your position. Is GPS on? You can type the address." }
        }
    }

    FarmButton(
        if (locating) "Getting your location…" else if (located) "Update from my location" else "Use my current location",
        { locating = true; note = null; locate() },
        Modifier.fillMaxWidth().padding(top = 8.dp),
        variant = ButtonVariant.Secondary, loading = locating, enabled = !locating, icon = Icons.Rounded.MyLocation,
    )
    note?.let {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val ok = located
            Icon(
                if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null,
                tint = if (ok) FarmColors.Primary else FarmColors.Warning, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            FText(it, variant = TextVariant.Caption)
        }
    }

    fun set(update: AddressForm.() -> AddressForm) = onChange(form.update())
    FarmInput(form.line1, { set { copy(line1 = it) } }, label = "House / street", error = errors["line1"])
    FarmInput(form.line2, { set { copy(line2 = it) } }, label = "Area or landmark (optional)")
    FarmInput(form.city, { set { copy(city = it) } }, label = "City", error = errors["city"])
    FarmInput(form.pincode, { set { copy(pincode = it) } }, label = "Pincode", error = errors["pincode"], keyboardType = KeyboardType.Number, maxLength = 6)
    FarmInput(form.contactPhone, { set { copy(contactPhone = it) } }, label = "Phone (optional)", error = errors["contactPhone"], keyboardType = KeyboardType.Phone)

    // The delivery pin: where the partner is sent. One line, editable on request (or when invalid).
    val pinError = errors["lat"] ?: errors["lng"]
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
            .background(if (located) FarmColors.PrimaryLight else FarmColors.Background).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn, null, tint = if (located) FarmColors.Primary else FarmColors.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("Delivery pin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                Text(
                    "${form.lat}, ${form.lng}" + if (located) " · from your phone" else " · tap Use my current location",
                    fontSize = 12.sp, color = FarmColors.TextLabel,
                )
            }
            Row(Modifier.clickable { editPin = !editPin }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Edit, null, tint = FarmColors.Primary, modifier = Modifier.size(16.dp))
                Text(" Edit", fontSize = 12.sp, color = FarmColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }
        if (editPin || pinError != null) {
            FarmInput(form.lat, { set { copy(lat = it) } }, label = "Latitude", error = errors["lat"], keyboardType = KeyboardType.Decimal)
            FarmInput(form.lng, { set { copy(lng = it) } }, label = "Longitude", error = errors["lng"], keyboardType = KeyboardType.Decimal)
        }
    }
}
