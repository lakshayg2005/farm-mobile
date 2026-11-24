package com.farm.customer.reservation

import com.farm.core.api.CropUpdateOut
import com.farm.core.api.Http
import com.farm.core.api.PaymentIntentCreate
import com.farm.core.api.ReservationOut
import com.farm.customer.orders.HarvestStage
import com.farm.customer.orders.harvestStageOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Reservation rail, deposit rules and crop timeline helpers (#42). */
class ReservationTest {
    @Test
    fun railFollowsTheSixStatuses() {
        assertEquals(1, reservationStage("PENDING"))
        assertEquals(2, reservationStage("ACCEPTED"))
        assertEquals(3, reservationStage("ACTIVE"))
        assertEquals(4, reservationStage("HARVEST_READY"))
        assertEquals(5, reservationStage("FULFILLING"))
        assertEquals(6, reservationStage("COMPLETED"))
        assertEquals("Harvested. Your harvest order is being prepared.", nextStepText("HARVEST_READY", null))
        assertEquals("Your harvest is ready: choose delivery or buy-back.", nextStepText("FULFILLING", null))
        listOf("REJECTED", "CANCELLED", "EXPIRED").forEach {
            assertEquals(0, reservationStage(it))
            assertTrue(isClosed(it))
        }
    }

    @Test
    fun pendingSaysHowLongTheFarmerHasLeft() {
        val now = Instant.parse("2026-11-20T10:00:00Z")
        assertEquals(
            "Waiting for the farmer. They have 47 h left to answer.",
            nextStepText("PENDING", "2026-11-22T09:30:00Z", now),
        )
        assertEquals(
            "Waiting for the farmer. They have 30 min left to answer.",
            nextStepText("PENDING", "2026-11-20T10:30:00Z", now),
        )
        assertEquals("Waiting for the farmer to answer (they have 48 hours).", nextStepText("PENDING", null, now))
    }

    @Test
    fun depositOnlyOnceAcceptedAndOnlyTheServerAmount() {
        assertTrue(canPayDeposit("ACCEPTED", 400.0))
        assertFalse(canPayDeposit("PENDING", 400.0))
        assertFalse(canPayDeposit("ACTIVE", 400.0))
        assertFalse(canPayDeposit("ACCEPTED", null))
        assertFalse(canPayDeposit("ACCEPTED", 0.0))
    }

    @Test
    fun depositIntentCarriesTheExactDepositAndTheReservationId() {
        val json = Http.encode(PaymentIntentCreate(1234.56, "DEPOSIT", "res-1"))
        assertEquals("""{"amount":1234.56,"kind":"DEPOSIT","referenceId":"res-1"}""", json)
    }

    @Test
    fun depositErrorsAreSpecific() {
        assertTrue(depositErrorMessage(409, "DEPOSIT_ALREADY_PAID", null).startsWith("The deposit is already paid"))
        assertEquals("The deposit of this reservation is 400.00", depositErrorMessage(422, "DEPOSIT_AMOUNT_MISMATCH", "The deposit of this reservation is 400.00"))
        assertTrue(depositErrorMessage(404, "RESERVATION_NOT_FOUND", "x").contains("Wait a few seconds"))
        assertTrue(depositErrorMessage(402, null, null).startsWith("Your payment was declined"))
    }

    @Test
    fun confirmedOnceTheReservationLeftAccepted() {
        assertFalse(depositConfirmed("ACCEPTED"))
        assertTrue(depositConfirmed("ACTIVE"))
        assertTrue(depositConfirmed("CANCELLED"))
    }

    @Test
    fun harvestChoiceOnlyWhileFulfilling() {
        assertTrue(canChooseHarvest("FULFILLING"))
        listOf("ACTIVE", "HARVEST_READY", "CANCELLED").forEach { assertFalse(it, canChooseHarvest(it)) }
    }

    @Test
    fun harvestChoiceNeedsFulfillingAndAnOrderWithoutAnOption() {
        val choose = harvestStageOf("PLACED", null)
        assertEquals(HarvestStage.CHOOSE, choose)
        assertTrue(showHarvestChoice("FULFILLING", choose))
        // land not caught up yet, or not loaded: do not offer the choice
        assertFalse(showHarvestChoice("HARVEST_READY", choose))
        assertFalse(showHarvestChoice(null, choose))
        // already chosen, or no harvest order yet
        assertFalse(showHarvestChoice("FULFILLING", harvestStageOf("READY_FOR_DISPATCH", "DELIVERY")))
        assertFalse(showHarvestChoice("FULFILLING", harvestStageOf(null, null)))
    }

    @Test
    fun cropUpdatesDecodeTheLandShape() {
        val list = Http.json.decodeFromString<List<CropUpdateOut>>(
            """[{"id":"u1","reservationId":"r1","stage":"SOWN","note":"Seeds in","mediaIds":["m1","m2"],
            "location":{"type":"Point","coordinates":[81.28,21.19]},"createdAt":"2026-11-01T08:00:00Z"}]""",
        )
        assertEquals(listOf("m1", "m2"), list[0].mediaIds)
        assertEquals(21.19, list[0].location!!.lat, 0.0)
        assertEquals("Sown", cropStageLabel(list[0].stage).label)
    }

    @Test
    fun reservationDecodesExpiresAt() {
        val r = Http.json.decodeFromString<ReservationOut>(
            """{"id":"r1","plotSeasonId":"s1","customerId":"c","farmerId":"f","cropId":"green_chilli","expectedQtyKg":200,
            "deposit":400.0,"total":2000.0,"status":"ACCEPTED","expiresAt":"2026-11-22T10:00:00Z"}""",
        )
        assertEquals("2026-11-22T10:00:00Z", r.expiresAt)
        assertEquals("Green chilli", cropName(r.cropId))
    }

    @Test
    fun onlyVerifiedPhotosGetTheGreenBadge() {
        assertEquals("Verified on site", photoBadge(true))
        assertEquals("Unverified", photoBadge(false))
    }
}
