package com.farm.core.ui

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
import com.farm.core.auth.AuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthMode { Otp, Password, SignUp }

private val PHONE_RE = Regex("^(0|\\+91)?[6-9]\\d{9}$")
private fun validPhone(raw: String) = PHONE_RE.matches(raw.replace(Regex("[\\s\\-()]"), ""))

/** OTP / password / sign-up login shared by both apps. Only accounts with [requiredRole] get in. */
@Composable
fun SharedLoginScreen(
    appName: String,
    appSubtitle: String?,
    requiredRole: String,
    logoEmoji: String,
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
            try {
                block()
            } catch (_: Exception) {
                // the message is in AuthManager.state.error
            } finally {
                loading = false
            }
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
        if (password.length < 6) {
            localError = "Please enter your password (minimum 6 characters)."
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
            password.length < 6 -> localError = "Choose a password of at least 6 characters."
            else -> run {
                onSuccess(AuthManager.register(name.trim(), phone, password, cleanEmail.ifEmpty { null }, requiredRole))
            }
        }
    }

    fun devLogin(role: String) {
        resetErrors()
        val user = AuthManager.devMockLogin(role)
        if (role != requiredRole) {
            localError = "Test Role Mismatch: Signed in as [$role], but this app requires [$requiredRole]. Role check verified!"
            return
        }
        onSuccess(user)
    }

    val onPhone: (String) -> Unit = { t ->
        phone = t.filter(Char::isDigit)
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
        Text(logoEmoji, fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        FText(appName, variant = TextVariant.H1, textAlign = TextAlign.Center)
        if (appSubtitle != null) {
            FText(appSubtitle, variant = TextVariant.Caption, color = Color(0xFF666666), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        Pill(
            "Role: ${requiredRole.uppercase()}", bg = FarmColors.PrimaryLight, fg = FarmColors.Primary,
            fontSize = 12, border = Color(0xFFC8E6C9), modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(20.dp))

        FarmCard(Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
            // Mode switcher
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F0EB)).padding(4.dp),
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
                        .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(8.dp)).padding(12.dp),
                ) {
                    Text("Authentication Error", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    Text(activeError, fontSize = 12.sp, color = Color(0xFFB71C1C))
                }
                Spacer(Modifier.height(14.dp))
            }

            when (mode) {
                AuthMode.Otp -> if (!otpStep) {
                    FarmInput(phone, onPhone, label = "Phone Number", placeholder = "98765 43210", prefix = "+91", keyboardType = KeyboardType.Phone, maxLength = 10)
                    FarmButton("Send OTP Code", ::sendOtp, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                } else {
                    FText("Enter the 6-digit code sent to +91 $phone", textAlign = TextAlign.Center, color = FarmColors.TextLabel, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    devCode?.let {
                        Pill("Dev Code auto-filled: $it", bg = Color(0xFFE3F2FD), fg = Color(0xFF1565C0), fontSize = 12, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    OtpInput(otp, { otp = it; if (localError != null) localError = null }, enabled = !loading)
                    FarmButton("Verify & Login", ::verifyOtp, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (countdown > 0) "Resend OTP in ${countdown}s" else "Resend OTP",
                            fontSize = 13.sp,
                            fontWeight = if (countdown > 0) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (countdown > 0) Color(0xFF999999) else FarmColors.Primary,
                            modifier = Modifier.clickable(enabled = countdown <= 0 && !loading) { sendOtp() },
                        )
                        Text(
                            "Change Number", fontSize = 13.sp, color = Color(0xFF666666), textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { otpStep = false; otp = ""; devCode = null; localError = null },
                        )
                    }
                }
                AuthMode.SignUp -> {
                    FarmInput(name, { name = it }, label = "Full Name", placeholder = "Anita Sharma")
                    FarmInput(phone, onPhone, label = "Phone Number", placeholder = "98765 43210", prefix = "+91", keyboardType = KeyboardType.Phone, maxLength = 10)
                    FarmInput(email, { email = it }, label = "Email (optional)", placeholder = "you@example.com", keyboardType = KeyboardType.Email)
                    FarmInput(password, { password = it }, label = "Password", placeholder = "At least 6 characters", isPassword = true)
                    FarmButton("Create Account", ::signUp, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                }
                AuthMode.Password -> {
                    FarmInput(phone, onPhone, label = "Phone Number", placeholder = "98765 43210", prefix = "+91", keyboardType = KeyboardType.Phone, maxLength = 10)
                    FarmInput(password, { password = it }, label = "Password", placeholder = "••••••••", isPassword = true)
                    FarmButton("Log In", ::passwordLogin, Modifier.fillMaxWidth().padding(top = 12.dp), loading = loading)
                }
            }

            // Dev quick test shortcuts
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(Modifier.height(16.dp))
            FText("🛠️ Quick Test Access (Dev Mode)", fontSize = 11.sp, color = Color(0xFF888888), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            val otherRole = if (requiredRole == "customer") "delivery" else "customer"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevButton("Mock ${requiredRole.uppercase()} Login", Color(0xFFE8F5E9), Color(0xFFC8E6C9), FarmColors.Primary, Modifier.weight(1f), !loading) { devLogin(requiredRole) }
                DevButton("Test Role Reject ($otherRole)", Color(0xFFFFF3E0), Color(0xFFFFE0B2), Color(0xFFE65100), Modifier.weight(1f), !loading) { devLogin(otherRole) }
            }

            ServerAddressEditor()
        }
    }
}

@Composable
private fun DevButton(label: String, bg: Color, border: Color, fg: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, textAlign = TextAlign.Center)
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
            FText("Server: $current", fontSize = 11.sp, color = Color(0xFF888888), modifier = Modifier.weight(1f))
            Text(
                "Change", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary,
                modifier = Modifier.clickable { draft = current; editing = true }.padding(4.dp),
            )
        }
    } else {
        FarmInput(draft, { draft = it }, label = "Server address", placeholder = "http://10.50.7.73/api/v1", keyboardType = KeyboardType.Uri)
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
