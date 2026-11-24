package com.farm.customer.reservation

import com.farm.customer.orders.HarvestStage
import com.farm.customer.orders.parseInstant
import java.time.Duration
import java.time.Instant

// Pure helpers for the reservation screen (#42, #43): no Android, so they are unit tested.

/** The five live stages, shown with the same rail as orders. */
val RESERVATION_STAGES = listOf(
    "PENDING" to "Pending",
    "ACCEPTED" to "Accepted",
    "ACTIVE" to "Active",
    "HARVEST_READY" to "Harvest ready",
    "FULFILLING" to "Fulfilling",
    "COMPLETED" to "Completed",
)

/** How many stages are done; 0 for REJECTED, CANCELLED, EXPIRED or an unknown status. */
fun reservationStage(status: String): Int =
    RESERVATION_STAGES.indexOfFirst { it.first == status }.let { if (it < 0) 0 else it + 1 }

fun isClosed(status: String): Boolean = status in setOf("REJECTED", "CANCELLED", "EXPIRED")

/** "What happens next" under the rail, in the customer's words. */
fun nextStepText(status: String, expiresAt: String?, now: Instant = Instant.now()): String = when (status) {
    "PENDING" -> {
        val left = expiresAt?.let(::parseInstant)?.let { Duration.between(now, it) }
        if (left != null && !left.isNegative) {
            val hours = left.toHours()
            "Waiting for the farmer. They have ${if (hours >= 1) "$hours h" else "${left.toMinutes()} min"} left to answer."
        } else {
            "Waiting for the farmer to answer (they have 48 hours)."
        }
    }
    "ACCEPTED" -> "The farmer accepted. Pay the deposit to confirm your season."
    "ACTIVE" -> "Your crop is growing. The farmer posts updates with photos below."
    "HARVEST_READY" -> "Harvested. Your harvest order is being prepared."
    "FULFILLING" -> "Your harvest is ready: choose delivery or buy-back."
    "COMPLETED" -> "Completed."
    "REJECTED" -> "The farmer turned this request down. Nothing was charged."
    "CANCELLED" -> "This reservation was cancelled. A paid deposit is refunded."
    "EXPIRED" -> "The farmer did not answer in 48 hours, so the request expired."
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

/** The land service lets the customer cancel while the reservation is live. */
fun canCancelReservation(status: String): Boolean = status in setOf("PENDING", "ACCEPTED", "ACTIVE", "HARVEST_READY")

/** What cancelling means for the money, shown before the customer confirms. */
fun cancelWarning(status: String): String = when (status) {
    "PENDING", "ACCEPTED" -> "The farmer will be told. Nothing has been paid yet, so nothing is charged."
    else -> "The farmer will be told and the season is freed. Your paid deposit is refunded."
}

/** Message for a refused POST /land/reservations/{id}/cancel. */
fun cancelReservationError(code: String?, serverMessage: String?): String = when (code) {
    "RESERVATION_NOT_CANCELLABLE" -> "This reservation can no longer be cancelled. Pull down to refresh."
    else -> serverMessage ?: "Could not cancel the reservation. Try again."
}

/** The deposit can be paid once the farmer has accepted, and only for the amount the server set. */
fun canPayDeposit(status: String, deposit: Double?): Boolean = status == "ACCEPTED" && deposit != null && deposit > 0

/** The harvest choice is offered only while the reservation is FULFILLING (#43). */
fun canChooseHarvest(status: String): Boolean = status == "FULFILLING"

/**
 * Show the DELIVERY / BUYBACK choice only when land says FULFILLING and the harvest order exists
 * without an option. The two services catch up through events, so one can be a few seconds ahead.
 */
fun showHarvestChoice(reservationStatus: String?, stage: HarvestStage): Boolean =
    reservationStatus != null && canChooseHarvest(reservationStatus) && stage == HarvestStage.CHOOSE

/** Message for a refused deposit payment (POST /order/orders/payments/intent, kind DEPOSIT). */
fun depositErrorMessage(status: Int, code: String?, serverMessage: String?): String = when {
    code == "DEPOSIT_ALREADY_PAID" -> "The deposit is already paid. Your reservation is being confirmed."
    code == "DEPOSIT_AMOUNT_MISMATCH" -> serverMessage ?: "The deposit amount changed. Pull down to refresh and try again."
    code == "RESERVATION_NOT_FOUND" ->
        "The payment service does not know this reservation yet. Wait a few seconds and try again."
    code == "RESERVATION_NOT_PAYABLE" -> "This reservation can no longer be paid."
    status == 402 || code == "PAYMENT_FAILED" -> "Your payment was declined. No money was taken, please try again."
    status == 401 -> "Please log in again to pay."
    else -> serverMessage ?: "Could not take the deposit. Try again."
}

/** True when the deposit's event round trip is done and the server shows the reservation as paid. */
fun depositConfirmed(status: String): Boolean = status != "ACCEPTED" && status != "PENDING"

/** After paying, poll every [DEPOSIT_POLL_MS] for at most [DEPOSIT_POLL_LIMIT_MS]. */
const val DEPOSIT_POLL_MS = 3_000L
const val DEPOSIT_POLL_LIMIT_MS = 60_000L

data class StageLabel(val label: String, val icon: String)

/** The farmer's crop update stages (land CropUpdateCreate: SOWN, GROWING, FLOWERING). */
fun cropStageLabel(stage: String): StageLabel = when (stage) {
    "SOWN" -> StageLabel("Sown", "SOWN")
    "GROWING" -> StageLabel("Growing", "GROWING")
    "FLOWERING" -> StageLabel("Flowering", "FLOWERING")
    else -> StageLabel(stage.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, "OTHER")
}

/** "wheat" -> "Wheat", "green_chilli" -> "Green chilli". */
fun cropName(cropId: String): String = cropId.replace('_', ' ').replace('-', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }

/** Badge text for a crop photo: only a photo the media service verified on site gets the green badge. */
fun photoBadge(isGeoVerified: Boolean): String = if (isGeoVerified) "Verified on site" else "Unverified"
