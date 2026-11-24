package com.farm.customer.checkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Use my current location" on the delivery address. */
class AddressLocationTest {
    @Test fun fillsTheFormFromTheFoundAddress() {
        val typed = AddressForm(line1 = "", line2 = "", city = "", pincode = "", contactPhone = "9876543210")
        val filled = typed.withLocation(FoundAddress(21.251234567, 81.629876543, "12 MG Road", "Civil Lines", "Raipur", "492001"))
        assertEquals("12 MG Road", filled.line1)
        assertEquals("Civil Lines", filled.line2)
        assertEquals("Raipur", filled.city)
        assertEquals("492001", filled.pincode)
        assertEquals("21.251235", filled.lat)
        assertEquals("81.629877", filled.lng)
        assertEquals("9876543210", filled.contactPhone) // never touched
        assertTrue(validateAddress(filled) is AddressResult.Ok)
    }

    @Test fun keepsWhatWasTypedWhenTheLookupFoundNothing() {
        val typed = AddressForm(line1 = "Flat 4, Green Park", city = "Durg", pincode = "491001")
        val filled = typed.withLocation(FoundAddress(21.19, 81.28, pincode = "4910")) // a bad pincode is ignored
        assertEquals("Flat 4, Green Park", filled.line1)
        assertEquals("Durg", filled.city)
        assertEquals("491001", filled.pincode)
        assertEquals("21.190000", filled.lat)
    }

    @Test fun changedFieldsNamesOnlyWhatChanged() {
        val a = AddressForm(line1 = "a", city = "x")
        assertEquals(setOf("line1", "lat", "lng"), changedFields(a, a.copy(line1 = "b", lat = "1", lng = "2")))
        assertEquals(emptySet<String>(), changedFields(a, a))
    }
}
