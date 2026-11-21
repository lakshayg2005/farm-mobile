package com.farm.delivery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.auth.AuthManager
import com.farm.core.ui.ButtonVariant
import com.farm.core.ui.CenteredLoading
import com.farm.core.ui.FText
import com.farm.core.ui.FarmButton
import com.farm.core.ui.FarmCard
import com.farm.core.ui.FarmColors
import com.farm.core.ui.Pill
import com.farm.core.ui.TextVariant

@Composable
fun DeliveryHomeScreen(onLogin: () -> Unit, onTeam: () -> Unit, onRoute: () -> Unit) {
    val auth by AuthManager.state.collectAsState()
    if (auth.isLoading) return CenteredLoading()

    Column(
        Modifier.fillMaxSize().background(FarmColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val user = auth.user
        if (!auth.isAuthenticated || user == null || !auth.hasRole("delivery")) {
            FarmCard(Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
                FText("🚚 Farm Delivery", variant = TextVariant.H1)
                FText(
                    "Welcome! Log in with your Delivery Partner account to access your assigned pooled routes and stops.",
                    color = FarmColors.TextLabel, modifier = Modifier.padding(top = 8.dp),
                )
                FarmButton("Log In / Sign Up", onLogin, Modifier.fillMaxWidth().padding(top = 24.dp))
                FarmButton("👥  Meet the Team", onTeam, Modifier.fillMaxWidth().padding(top = 8.dp), variant = ButtonVariant.Ghost)
            }
            return@Column
        }

        FarmCard(Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚚", fontSize = 36.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    FText(user.name.ifEmpty { "Delivery Partner" }, variant = TextVariant.H2)
                    FText(user.phone, variant = TextVariant.Caption)
                }
            }
            Pill("Active Delivery Partner", Color(0xFFE3F2FD), Color(0xFF1565C0), Modifier.padding(vertical = 10.dp), fontSize = 12)

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFAFAFA))
                    .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(12.dp),
            ) {
                FText("Partner Details", variant = TextVariant.Label)
                FText("Roles: ${user.roles.joinToString(", ")}", modifier = Modifier.padding(top = 2.dp))
                FText("Partner ID: ${user.id}", variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
            }

            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F4F8))
                    .border(BorderStroke(1.dp, Color(0xFFD0DCE8)), RoundedCornerShape(8.dp)).padding(12.dp),
            ) {
                FText("Today's Route", variant = TextVariant.H2, fontSize = 16.sp)
                FText("• Pickups first, then drops — ordered by proximity", variant = TextVariant.Caption, modifier = Modifier.padding(top = 4.dp))
                FText("• Tap \"Navigate\" on each stop to open Google Maps", variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
                FText("• OTP displayed for contactless drop confirmation", variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp))
            }

            FarmButton("🗺️  View Today's Route", onRoute, Modifier.fillMaxWidth().padding(top = 16.dp))
            FarmButton("👥  Meet the Team", onTeam, Modifier.fillMaxWidth().padding(top = 10.dp), variant = ButtonVariant.Secondary)
            FarmButton("Log Out", { AuthManager.logout() }, Modifier.fillMaxWidth().padding(top = 10.dp), variant = ButtonVariant.Ghost)
        }
    }
}
