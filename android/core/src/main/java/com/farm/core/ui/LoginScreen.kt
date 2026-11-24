package com.farm.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.ApiConfig
import com.farm.core.api.User
import com.farm.core.api.load
import com.farm.core.auth.AuthManager
import com.farm.core.auth.isValidMobile
import com.farm.core.auth.localMobileDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthMode { Otp, Password, SignUp }

private fun validPhone(raw: String) = isValidMobile(raw)

/** OTP / password / sign-up login shared by both apps. Only accounts with [requiredRole] get in. */
@Composable
fun SharedLoginScreen(
    appName: String,
    appSubtitle: String?,
    requiredRole: String,
    logoIcon: ImageVector,
    onSuccess: (User) -> Unit,
) {
    val auth by AuthManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.Otp) }
    var otpStep by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var devCode by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    val activeError = localError ?: auth.error
    fun resetErrors() {
        localError = null
        AuthManager.clearError()
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            // a failure is already in AuthManager.state (error and per-field messages)
            load { block() }
            loading = false
        }
    }

    fun sendOtp() {
        resetErrors()
        if (!validPhone(phone)) {
            localError = "Please enter a valid 10-digit mobile number."
            return
        }
        run {
            val res = AuthManager.requestOtp(phone)
            res.devCode?.let { devCode = it; otp = it }
            countdown = if (res.resendAfter > 0) res.resendAfter else 60
            otpStep = true
        }
    }

    fun verifyOtp() {
        resetErrors()
        if (otp.length != 6) {
            localError = "Please enter the complete 6-digit OTP."
            return
        }
        run { onSuccess(AuthManager.verifyOtp(phone, otp, requiredRole)) }
    }

    fun passwordLogin() {
        resetErrors()
        if (!validPhone(phone)) {
            localError = "Please enter a valid 10-digit mobile number."
            return
        }
        if (password.isEmpty()) {
            localError = "Please enter your password."
            return
        }
        run { onSuccess(AuthManager.loginWithPassword(phone, password, requiredRole)) }
    }

    fun signUp() {
        resetErrors()
        val cleanEmail = email.trim()
        when {
            name.trim().length < 2 -> localError = "Please enter your full name."
            !validPhone(phone) -> localError = "Please enter a valid 10-digit mobile number."
            cleanEmail.isNotEmpty() && !Regex("^\\S+@\\S+\\.\\S+$").matches(cleanEmail) ->
                localError = "Please enter a valid email address or leave it blank."
            password.length < 8 -> localError = "Choose a password of at least 8 characters."
            else -> run {
                onSuccess(AuthManager.register(name.trim(), phone, password, cleanEmail.ifEmpty { null }, requiredRole))
            }
        }
    }

    val onPhone: (String) -> Unit = { t ->
        phone = localMobileDigits(t)
        if (localError != null) localError = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FarmColors.Background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(FarmColors.PrimaryLight), contentAlignment = Alignment.Center) {
            Icon(logoIcon, contentDescription = null, tint = FarmColors.Primary, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(8.dp))
        FText(appName, variant = TextVariant.H1, textAlign = TextAlign.Center)
        if (appSubtitle != null) {
            FText(appSubtitle, variant = TextVariant.Caption, color = FarmColors.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        Pill(
            "Role: ${requiredRole.uppercase()}", bg = FarmColors.PrimaryLight, fg = FarmColors.Primary,
            fontSize = 12, border = FarmColors.PrimaryLight, modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(20.dp))

        FarmCard(Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
            // Mode switcher
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FarmColors.Subtle).padding(4.dp),
            ) {
                listOf(AuthMode.Otp to "OTP Login", AuthMode.Password to "Password", AuthMode.SignUp to "Sign Up").forEach { (m, label) ->
                    val active = mode == m
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable { mode = m; resetErrors() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) FarmColors.Primary else FarmColors.TextMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (activeError != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FarmColors.ErrorBg)
                        .border(1.dp, FarmColors.ErrorBg, RoundedCornerShape(8.dp)).padding(12.dp),
                ) {
                    Text("Authentication Error", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FarmColors.Error)
                    Text(activeError, fontSize = 12.sp, color = FarmColors.Error)
                }
                Spacer(Modifier.height(14.dp))
            }

            when (mode) {
                AuthMode.Otp -> if (!otpStep) {
                    FarmInput(phone, onPhone, label = "Phone Number", placeholder = "98765 43210", prefix = "+91", keyboardType = KeyboardType.Phone, maxLength = 16, error = auth.fieldErrors["phone"])
                    FarmButton("Send OTP Code", ::sendOtp, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                } else {
                    FText("Enter the 6-digit code sent to +91 $phone", textAlign = TextAlign.Center, color = FarmColors.TextLabel, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    devCode?.let {
                        Pill("Dev Code auto-filled: $it", bg = FarmColors.InfoBg, fg = FarmColors.Info, fontSize = 12, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    OtpInput(otp, { otp = it; if (localError != null) localError = null }, enabled = !loading)
                    FarmButton("Verify & Login", ::verifyOtp, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (countdown > 0) "Resend OTP in ${countdown}s" else "Resend OTP",
                            fontSize = 13.sp,
                            fontWeight = if (countdown > 0) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (countdown > 0) FarmColors.TextMuted else FarmColors.Primary,
                            modifier = Modifier.clickable(enabled = countdown <= 0 && !loading) { sendOtp() },
                        )
                        Text(
                            "Change Number", fontSize = 13.sp, color = FarmColors.TextMuted, textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { otpStep = false; otp = ""; devCode = null; localError = null },
                        )
                    }
                }
                AuthMode.SignUp -> {
                    FarmInput(name, { name = it }, label = "Full Name", placeholder = "Anita Sharma", error = auth.fieldErrors["name"])
                    FarmInput(phone, onPhone, label = "Phone Number", placeholder = "98765 43210", prefix = "+91", keyboardType = KeyboardType.Phone, maxLength = 16, error = auth.fieldErrors["phone"])
                    FarmInput(email, { email = it }, label = "Email (optional)", placeholder = "you@example.com", keyboardType = KeyboardType.Email, error = auth.fieldErrors["email"])
                    FarmInput(password, { password = it }, label = "Password", placeholder = "At least 8 characters", isPassword = true, error = auth.fieldErrors["password"])
                    FarmButton("Create Account", ::signUp, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                }
                AuthMode.Password -> {
                    FarmInput(phone, onPhone, label = "Phone Number", placeholder = "98765 43210", prefix = "+91", keyboardType = KeyboardType.Phone, maxLength = 16, error = auth.fieldErrors["phone"])
                    FarmInput(password, { password = it }, label = "Password", placeholder = "••••••••", isPassword = true, error = auth.fieldErrors["password"])
                    FarmButton("Log In", ::passwordLogin, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                }
            }

            Spacer(Modifier.height(16.dp))
            ServerAddressEditor()
        }
    }
}

/** Lets the tester point the APK at another gateway without rebuilding. */
@Composable
private fun ServerAddressEditor() {
    var editing by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(ApiConfig.baseUrl) }
    var draft by remember { mutableStateOf(current) }

    Spacer(Modifier.height(14.dp))
    if (!editing) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FText("Server: $current", fontSize = 11.sp, color = FarmColors.TextMuted, modifier = Modifier.weight(1f))
            Text(
                "Change", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary,
                modifier = Modifier.clickable { draft = current; editing = true }.padding(4.dp),
            )
        }
    } else {
        FarmInput(draft, { draft = it }, label = "Server address", placeholder = "http://10.1.75.79:4501/api/v1", keyboardType = KeyboardType.Uri)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FarmButton("Reset", {
                ApiConfig.setBaseUrl(null); current = ApiConfig.baseUrl; editing = false
            }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
            FarmButton("Save", {
                ApiConfig.setBaseUrl(draft); current = ApiConfig.baseUrl; editing = false
            }, Modifier.weight(1f))
        }
    }
}
