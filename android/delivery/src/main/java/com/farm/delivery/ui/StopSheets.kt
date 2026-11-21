package com.farm.delivery.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.farm.core.api.LogisticsApi
import com.farm.core.api.MediaApi
import com.farm.core.ui.Banner
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.TextVariant
import com.farm.delivery.route.RouteStop
import com.farm.delivery.route.newPhotoUri
import com.farm.delivery.route.readPhotoAsJpeg
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSheet(title: String, stop: RouteStop, busy: Boolean, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, sheetState = sheetState, containerColor = FarmColors.Background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FText(title, variant = TextVariant.H2)
                    FText("${stop.customerName} · ${stop.address}", variant = TextVariant.Caption)
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEEEEEE)).clickable(enabled = !busy, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 16.sp, color = FarmColors.TextLabel) }
            }
            content()
        }
    }
}

@Composable
private fun NotesField(value: String, onChange: (String) -> Unit, placeholder: String, enabled: Boolean) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.dp, FarmColors.Border, RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Color(0xFF999999), fontSize = 14.sp)
        BasicTextField(value, onChange, enabled = enabled, minLines = 2, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
    }
}

/** Confirm a farm pickup: tick each item off, optional notes, POST /logistics/stops/{id}/pickup. */
@Composable
fun PickupSheet(stop: RouteStop, onClose: () -> Unit, onSuccess: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val checked = remember(stop.id) { mutableStateListOf<Boolean>().apply { repeat(stop.items.size) { add(false) } } }
    var notes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val allChecked = stop.items.isEmpty() || checked.all { it }

    StopSheet("Confirm Farm Pickup", stop, submitting, onClose) {
        error?.let { Banner("⚠️ $it", FarmColors.ErrorBg, Color(0xFFC62828)) }

        FarmCard(Modifier.fillMaxWidth()) {
            FText("Verify Items with Farmer (${stop.items.size} items)", variant = TextVariant.Label)
            FText("Tap each item to check it off as you load it:", variant = TextVariant.Caption, modifier = Modifier.padding(bottom = 8.dp))
            stop.items.forEachIndexed { idx, item ->
                val on = checked[idx]
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color(0xFFE8F5E9) else Color(0xFFFAFAFA))
                        .clickable(enabled = !submitting) { checked[idx] = !on }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (on) FarmColors.Primary else Color.White)
                            .border(1.5.dp, if (on) FarmColors.Primary else Color(0xFFBBBBBB), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item, fontSize = 15.sp,
                        color = if (on) Color(0xFF888888) else FarmColors.TextBody,
                        textDecoration = if (on) TextDecoration.LineThrough else null,
                    )
                }
            }
        }

        FarmCard(Modifier.fillMaxWidth()) {
            FText("Farmer / Quality Notes (Optional)", variant = TextVariant.Label, modifier = Modifier.padding(bottom = 8.dp))
            NotesField(notes, { notes = it }, "e.g. All crates in good condition, fresh harvest", !submitting)
        }

        FarmButton(
            if (submitting) "Confirming Pickup..." else "📦 Confirm All Items Picked Up",
            {
                submitting = true
                error = null
                scope.launch {
                    try {
                        LogisticsApi.submitPickup(stop.id, notes.trim().ifEmpty { null })
                        onSuccess(stop.id)
                        onClose()
                    } catch (e: Exception) {
                        error = e.message ?: "Pickup confirmation failed"
                        submitting = false
                    }
                }
            },
            Modifier.fillMaxWidth(), loading = submitting, enabled = allChecked && !submitting,
        )
        FarmButton("Cancel", onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, enabled = !submitting)
    }
}

/** Proof of delivery: photo + the customer's 4-digit OTP, uploaded to media then POSTed to logistics. */
@Composable
fun ProofOfDeliverySheet(stop: RouteStop, onClose: () -> Unit, onSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var otp by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            photoUri = pendingCameraUri
            error = null
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoUri = uri
            error = null
        }
    }
    fun takePhoto() {
        try {
            val uri = newPhotoUri(context)
            pendingCameraUri = uri
            camera.launch(uri)
        } catch (e: Exception) {
            error = e.message ?: "Failed to launch camera"
        }
    }
    fun pickPhoto() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val expected = stop.otp
    val otpComplete = otp.length == 4
    val otpValid = if (expected != null) otp == expected else otpComplete
    val canSubmit = photoUri != null && otpValid && !submitting

    StopSheet("Proof of Delivery", stop, submitting, onClose) {
        error?.let { Banner("⚠️ $it", FarmColors.ErrorBg, Color(0xFFC62828)) }

        FarmCard(Modifier.fillMaxWidth()) {
            FText("1. Delivery Photo Proof (Required)", variant = TextVariant.Label)
            FText("Take a clear photo of the delivered package at customer location.", variant = TextVariant.Caption, modifier = Modifier.padding(bottom = 10.dp))
            val uri = photoUri
            if (uri != null) {
                AsyncImage(
                    model = uri, contentDescription = "Proof photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmButton("📷 Retake Photo", ::takePhoto, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !submitting)
                    FarmButton("🖼️ Choose Different", ::pickPhoto, Modifier.weight(1f), variant = ButtonVariant.Secondary, enabled = !submitting)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoButton("📷", "Take Photo", FarmColors.Primary, Color.White, Modifier.weight(1f), !submitting, ::takePhoto)
                    PhotoButton("🖼️", "Gallery", Color(0xFFE3F2FD), Color(0xFF1565C0), Modifier.weight(1f), !submitting, ::pickPhoto)
                }
            }
        }

        FarmCard(Modifier.fillMaxWidth()) {
            FText("2. Customer OTP Verification (Required)", variant = TextVariant.Label)
            FText("Ask the customer for the 4-digit code shown on their order screen.", variant = TextVariant.Caption, modifier = Modifier.padding(bottom = 10.dp))
            val borderColor = when {
                !otpComplete -> FarmColors.Border
                otpValid -> Color(0xFF2E7D32)
                else -> Color(0xFFC62828)
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(2.dp, borderColor, RoundedCornerShape(10.dp)).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (otp.isEmpty()) Text("0 0 0 0", fontSize = 26.sp, color = Color(0xFFBBBBBB), letterSpacing = 8.sp)
                BasicTextField(
                    value = otp,
                    onValueChange = { otp = it.filter(Char::isDigit).take(4) },
                    enabled = !submitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, textAlign = TextAlign.Center, color = FarmColors.TextDark),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (otpComplete) {
                Text(
                    if (otpValid) "✓ OTP matches! Delivery verified." else "✗ Invalid OTP. Please confirm the 4-digit code with customer.",
                    color = if (otpValid) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        FarmCard(Modifier.fillMaxWidth()) {
            FText("3. Delivery Note (Optional)", variant = TextVariant.Label, modifier = Modifier.padding(bottom = 8.dp))
            NotesField(notes, { notes = it }, "e.g. Handed to customer, placed on doorstep", !submitting)
        }

        FarmButton(
            if (submitting) "Submitting Delivery..." else "✅ Complete Delivery",
            {
                val uri = photoUri ?: return@FarmButton
                submitting = true
                error = null
                scope.launch {
                    try {
                        val bytes = readPhotoAsJpeg(context, uri)
                        val mediaId = MediaApi.uploadDeliveryPhoto(bytes, "image/jpeg", stop.id)
                        LogisticsApi.submitDelivery(stop.id, otp, mediaId, notes.trim().ifEmpty { null })
                        onSuccess(stop.id)
                        onClose()
                    } catch (e: Exception) {
                        error = e.message ?: "Delivery submission failed"
                        submitting = false
                    }
                }
            },
            Modifier.fillMaxWidth(), loading = submitting, enabled = canSubmit,
        )
        FarmButton("Cancel", onClose, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, enabled = !submitting)
    }
}

@Composable
private fun PhotoButton(icon: String, label: String, bg: Color, fg: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).clickable(enabled = enabled, onClick = onClick).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 28.sp)
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
