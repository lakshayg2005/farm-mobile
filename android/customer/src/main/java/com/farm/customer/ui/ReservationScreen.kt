package com.farm.customer.ui

import com.farm.core.ui.rememberRefreshTick
import com.farm.core.ui.PullRefresh

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.farm.core.ui.IconLabel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.farm.core.api.AgreementOut
import com.farm.core.api.ApiException
import com.farm.core.api.CatalogApi
import com.farm.core.api.CropUpdateOut
import com.farm.core.api.LandApi
import com.farm.core.api.MediaOut
import com.farm.core.api.OrderApi
import com.farm.core.api.PaymentIntent
import com.farm.core.api.PaymentIntentCreate
import com.farm.core.api.ReservationOut
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.Pill
import com.farm.core.ui.ScreenHeader
import com.farm.core.ui.TextVariant
import com.farm.customer.checkout.formatQty
import com.farm.customer.orders.formatDate
import com.farm.customer.orders.formatDateTime
import com.farm.core.api.load
import com.farm.core.api.loadErrorMessage
import com.farm.customer.orders.rupees
import com.farm.customer.reservation.DEPOSIT_POLL_LIMIT_MS
import com.farm.customer.reservation.DEPOSIT_POLL_MS
import com.farm.customer.reservation.RESERVATION_STAGES
import com.farm.customer.reservation.canCancelReservation
import com.farm.customer.reservation.canChooseHarvest
import com.farm.customer.reservation.cancelReservationError
import com.farm.customer.reservation.cancelWarning
import com.farm.customer.reservation.canPayDeposit
import com.farm.customer.reservation.cropName
import com.farm.customer.reservation.cropStageLabel
import com.farm.customer.reservation.depositConfirmed
import com.farm.customer.reservation.depositErrorMessage
import com.farm.customer.reservation.isClosed
import com.farm.customer.reservation.nextStepText
import com.farm.customer.reservation.photoBadge
import com.farm.customer.reservation.reservationStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ErrorRed = FarmColors.Error

/** Statuses after which the farmer posts crop updates. */
private val GROWING = setOf("ACTIVE", "HARVEST_READY", "FULFILLING")

private suspend fun findReservation(id: String): ReservationOut? = LandApi.getMyReservations().find { it.id == id }

/**
 * One reservation from the server (#42): where it is on the rail, the agreement, paying the
 * deposit (then a bounded wait for ACTIVE), and the farmer's crop updates with their photos.
 * When the harvest passed its check (FULFILLING) it leads to the harvest choice (#43).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(reservationId: String, onBack: () -> Unit, onHarvest: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var reservation by remember { mutableStateOf<ReservationOut?>(null) }
    var agreement by remember { mutableStateOf<AgreementOut?>(null) }
    var updates by remember { mutableStateOf<List<CropUpdateOut>?>(null) }
    var updatesError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf(false) }
    var payError by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var confirmSlow by remember { mutableStateOf(false) }
    var askCancel by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var cancelError by remember { mutableStateOf<String?>(null) }

    // Load, then refresh every 30 s while the reservation is still moving, so a new crop update
    // posted on the website appears without leaving the screen.
    val tick = rememberRefreshTick()
    LaunchedEffect(reservationId, reload, tick) {
        while (true) {
            load { findReservation(reservationId) }
                .onSuccess { reservation = it; error = null }
                .onFailure { error = loadErrorMessage(it) }
            val r = reservation
            if (r != null && reservationStage(r.status) >= 2 && agreement == null) {
                load { LandApi.getAgreement(reservationId) }.onSuccess { agreement = it }
            }
            if (r != null && r.status in GROWING) {
                load { LandApi.getCropUpdates(reservationId) }
                    .onSuccess { updates = it; updatesError = null }
                    .onFailure { updatesError = loadErrorMessage(it) }
            }
            loaded = true
            refreshing = false
            val status = reservation?.status ?: break
            if (isClosed(status)) break
            delay(30_000)
        }
    }

    // After the deposit is taken, land activates the reservation through an event: wait for it,
    // a few seconds at most, with a visible "Confirming your payment" state.
    LaunchedEffect(confirming) {
        if (!confirming) return@LaunchedEffect
        confirmSlow = false
        var waited = 0L
        while (waited < DEPOSIT_POLL_LIMIT_MS) {
            delay(DEPOSIT_POLL_MS)
            waited += DEPOSIT_POLL_MS
            load { findReservation(reservationId) }.onSuccess { if (it != null) reservation = it }
            if (reservation?.let { depositConfirmed(it.status) } == true) {
                confirming = false
                reload++ // fetch the timeline and agreement for the new status
                return@LaunchedEffect
            }
        }
        confirming = false
        confirmSlow = true
    }

    fun payDeposit(r: ReservationOut) {
        val amount = r.deposit ?: return
        paying = true
        payError = null
        confirmSlow = false
        scope.launch {
            load { OrderApi.createPaymentIntent(PaymentIntentCreate(amount, "DEPOSIT", r.id)) }
                .onSuccess { res ->
                    val body = res.error()
                    when {
                        res.ok && runCatching { res.decode<PaymentIntent>().status }.getOrNull() == "FAILED" ->
                            payError = depositErrorMessage(402, "PAYMENT_FAILED", null)
                        res.ok || body?.code == "DEPOSIT_ALREADY_PAID" -> confirming = true
                        else -> payError = depositErrorMessage(res.status, body?.code, body?.message)
                    }
                }
                .onFailure { payError = loadErrorMessage(it) }
            paying = false
        }
    }

    fun cancelReservation() {
        askCancel = false
        cancelling = true
        cancelError = null
        scope.launch {
            load { LandApi.cancelReservation(reservationId) }
                .onSuccess { reservation = it; reload++ }
                .onFailure { e ->
                    cancelError = (e as? ApiException)?.let { cancelReservationError(it.code, it.message) } ?: loadErrorMessage(e)
                }
            cancelling = false
        }
    }

    if (!loaded) return CenteredLoading("Loading your reservation…")
    if (askCancel) {
        AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Cancel this reservation?") },
            text = { Text(cancelWarning(reservation?.status ?: "")) },
            confirmButton = { TextButton(::cancelReservation) { Text("Cancel reservation", color = ErrorRed) } },
            dismissButton = { TextButton({ askCancel = false }) { Text("Keep it") } },
        )
    }
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader("Your reservation", onBack)
        val r = reservation
        if (r == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FText(if (error == null) "Reservation not found" else "Could not load the reservation", variant = TextVariant.H2)
                error?.let { FText(it, variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
                if (error != null) FarmButton("Try again", { loaded = false; reload++ }, Modifier.padding(top = 16.dp))
                FarmButton("Back", onBack, Modifier.padding(top = 12.dp), variant = ButtonVariant.Secondary)
            }
            return@Column
        }
        PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(r, agreement)
                when {
                    confirming -> FarmCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = FarmColors.Primary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            FText("Confirming your payment…", variant = TextVariant.Label)
                        }
                        FText("The deposit is paid. Your reservation turns Active in a few seconds.", variant = TextVariant.Caption, modifier = Modifier.padding(top = 6.dp))
                    }
                    canPayDeposit(r.status, r.deposit) -> DepositCard(r.deposit ?: 0.0, paying, payError, confirmSlow) { payDeposit(r) }
                    canChooseHarvest(r.status) -> FarmCard(
                        Modifier.fillMaxWidth(), background = FarmColors.PrimaryLight, border = BorderStroke(1.dp, FarmColors.PrimaryLight),
                    ) {
                        FText("Your harvest is ready", variant = TextVariant.H2, fontSize = 17.sp)
                        FText("Have it delivered to you, or let the farmer buy it back.", variant = TextVariant.Caption)
                        FarmButton("Choose delivery or buy-back", { onHarvest(r.id) }, Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                }
                if (r.status in GROWING) Timeline(updates, updatesError) { reload++ }
                if (canCancelReservation(r.status) && !confirming) {
                    cancelError?.let { Text(it, color = ErrorRed, fontSize = 13.sp) }
                    FarmButton(
                        if (cancelling) "Cancelling…" else "Cancel reservation", { askCancel = true },
                        Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, enabled = !cancelling,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(r: ReservationOut, agreement: AgreementOut?) {
    FarmCard(Modifier.fillMaxWidth()) {
        FText(cropName(r.cropId), variant = TextVariant.H1, fontSize = 24.sp)
        agreement?.let {
            FText("${it.plotTitle} · ${it.season.lowercase().replaceFirstChar { c -> c.uppercase() }}", variant = TextVariant.Caption)
            if (it.startDate.isNotEmpty() && it.endDate.isNotEmpty()) {
                FText("${formatDate(it.startDate)} to ${formatDate(it.endDate)}", variant = TextVariant.Caption)
            }
        }
        if (isClosed(r.status)) {
            FText(r.status.lowercase().replaceFirstChar { it.uppercase() }, variant = TextVariant.Label, color = ErrorRed, modifier = Modifier.padding(top = 10.dp))
            FText(nextStepText(r.status, r.expiresAt), variant = TextVariant.Caption)
        } else {
            StatusRail(reservationStage(r.status), nextStepText(r.status, r.expiresAt), Modifier.padding(top = 12.dp), total = RESERVATION_STAGES.size)
            FText(RESERVATION_STAGES.joinToString(" · ") { it.second }, variant = TextVariant.Caption, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Column(Modifier.padding(top = 10.dp)) {
            Line("Expected harvest", "${formatQty(r.expectedQtyKg)} kg")
            agreement?.takeIf { it.pricePerKg > 0 }?.let { Line("Price", "${rupees(it.pricePerKg)} / kg") }
            r.total?.let { Line("Total", rupees(it)) }
            r.deposit?.let { Line("Deposit (20%)", rupees(it)) }
            agreement?.takeIf { it.balance > 0 }?.let { Line("Balance at harvest", rupees(it.balance)) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        FText(label, variant = TextVariant.Caption, modifier = Modifier.weight(1f))
        FText(value, variant = TextVariant.Caption, color = FarmColors.TextDark)
    }
}

@Composable
private fun DepositCard(deposit: Double, paying: Boolean, error: String?, slow: Boolean, onPay: () -> Unit) {
    FarmCard(Modifier.fillMaxWidth()) {
        FText("Pay the deposit", variant = TextVariant.H2, fontSize = 18.sp)
        FText("The farmer accepted your request. The deposit of ${rupees(deposit)} confirms the season; the balance is paid at harvest.", variant = TextVariant.Caption)
        FText("This is a test payment: no real money is taken.", variant = TextVariant.Caption)
        if (slow) {
            FText("We are still waiting for the confirmation. Pull down to refresh in a moment.", variant = TextVariant.Caption, color = FarmColors.Warning, modifier = Modifier.padding(top = 6.dp))
        }
        error?.let { Text(it, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        FarmButton(
            if (paying) "Paying…" else "Pay ${rupees(deposit)}", onPay,
            Modifier.fillMaxWidth().padding(top = 10.dp), loading = paying, enabled = !paying,
        )
    }
}

@Composable
private fun Timeline(updates: List<CropUpdateOut>?, error: String?, onRetry: () -> Unit) {
    FText("Crop timeline", variant = TextVariant.H2, fontSize = 18.sp)
    when {
        updates == null && error != null -> FarmCard(Modifier.fillMaxWidth()) {
            FText("Could not load the crop updates", variant = TextVariant.Label)
            FText(error, variant = TextVariant.Caption)
            FarmButton("Try again", onRetry, Modifier.padding(top = 8.dp), variant = ButtonVariant.Secondary)
        }
        updates == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = FarmColors.Primary)
        }
        updates.isEmpty() -> FarmCard(Modifier.fillMaxWidth()) {
            FText("No updates yet", variant = TextVariant.Label)
            FText("The farmer posts photos as the crop grows. They appear here.", variant = TextVariant.Caption)
        }
        else -> updates.forEach { u ->
            val stage = cropStageLabel(u.stage)
            FarmCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconLabel(
                        when (stage.icon) {
                            "SOWN" -> Icons.Rounded.Grass
                            "GROWING" -> Icons.Rounded.Eco
                            "FLOWERING" -> Icons.Rounded.LocalFlorist
                            else -> Icons.Rounded.Timeline
                        },
                        stage.label, Modifier.weight(1f), color = FarmColors.TextDark, iconColor = FarmColors.Primary,
                        fontSize = 14, fontWeight = FontWeight.SemiBold,
                    )
                    if (u.createdAt.isNotEmpty()) FText(formatDateTime(u.createdAt), variant = TextVariant.Caption)
                }
                u.note?.takeIf { it.isNotBlank() }?.let {
                    Text("“$it”", fontStyle = FontStyle.Italic, fontSize = 14.sp, color = FarmColors.TextBody, modifier = Modifier.padding(top = 6.dp))
                }
                u.mediaIds.forEach { CropPhoto(it) }
            }
        }
    }
}

/** One photo of a crop update: GET /media/{id} for its URL and whether it was taken on the plot. */
@Composable
private fun CropPhoto(mediaId: String) {
    var media by remember(mediaId) { mutableStateOf<MediaOut?>(null) }
    var failed by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId) {
        load { CatalogApi.getMedia(mediaId) }.onSuccess { media = it }.onFailure { failed = true }
    }
    Box(
        Modifier.fillMaxWidth().padding(top = 10.dp).height(170.dp).clip(RoundedCornerShape(10.dp)).background(FarmColors.Subtle),
        contentAlignment = Alignment.Center,
    ) {
        val m = media
        val url = m?.url ?: m?.thumbUrl
        when {
            url != null -> AsyncImage(model = url, contentDescription = "Crop photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            failed -> FText("Photo unavailable", variant = TextVariant.Caption)
            else -> CircularProgressIndicator(Modifier.size(24.dp), color = FarmColors.Primary, strokeWidth = 2.dp)
        }
        if (m != null) {
            Pill(
                photoBadge(m.isGeoVerified),
                if (m.isGeoVerified) FarmColors.PrimaryLight else FarmColors.WarningBg,
                if (m.isGeoVerified) FarmColors.Primary else FarmColors.Warning,
                Modifier.align(Alignment.TopStart).padding(8.dp),
                icon = if (m.isGeoVerified) Icons.Rounded.Verified else Icons.Rounded.ErrorOutline,
            )
        }
    }
}
