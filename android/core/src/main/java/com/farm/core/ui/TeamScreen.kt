package com.farm.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TeamMember(val name: String, val github: String, val role: String, val built: String)
data class TeamSection(val name: String, val about: String, val members: List<TeamMember>)
data class GuideInfo(val name: String, val role: String, val thanks: String)

val guide = GuideInfo(
    name = "Dr Gagan Sir",
    role = "Our guide and mentor",
    thanks = "Thank you, Sir, for your guidance, patience and the push to build something real. This project exists because of your support.",
)

val teams = listOf(
    TeamSection(
        "Team 1 · Mobile apps",
        "Customer app and delivery app (native Android, Kotlin + Jetpack Compose).",
        listOf(
            TeamMember("Shashank Yadav", "iamshashankyadav", "Team lead", "Mobile project setup, delivery app proof of delivery, OTP and live location pings"),
            TeamMember("Chetan", "Chetan10R15", "Member", "Customer app: browse produce near me, land tab"),
            TeamMember("Rishi", "krish1809", "Member", "Order service (checkout, payments, harvest orders) and the customer cart"),
            TeamMember("Kabeer", "kabeer-m", "Member", "Shared login screens, delivery route screen, notification service API"),
        ),
    ),
    TeamSection(
        "Team 2 · Website",
        "Farmer, customer and admin website (React).",
        listOf(
            TeamMember("Lakshay", "lakshayg2005", "Team lead", "Backend skeleton, identity, CI, gateway, data seeder, website login, land search and reservations, admin and inspector pages"),
            TeamMember("Rohit", "rohitraghuwanshi07", "Member", "Land and quality services, farmer plot drawing page"),
            TeamMember("Akshat", "Akshats-git", "Member", "Website setup, catalog service and photo (media) service"),
            TeamMember("Aman", "Aman-maths", "Member", "Team contributions to the website"),
        ),
    ),
)

@Composable
fun TeamScreen(onBack: () -> Unit, title: String = "Our Team") {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        ScreenHeader(title, onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
        ) {
            // Mentor card
            FarmCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, FarmColors.PrimaryLight)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(68.dp).clip(CircleShape).background(FarmColors.PrimaryLight)
                            .border(2.dp, FarmColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.School, null, tint = FarmColors.Primary, modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Pill("PROJECT GUIDE & MENTOR", FarmColors.PrimaryLight, FarmColors.Primary)
                        FText(guide.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark, modifier = Modifier.padding(top = 4.dp))
                        FText(guide.role, fontSize = 13.sp, color = FarmColors.TextLabel)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = FarmColors.Border)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FarmColors.Background).padding(12.dp),
                ) {
                    Text("“", fontSize = 28.sp, color = FarmColors.Primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(guide.thanks, fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, color = FarmColors.TextDark)
                }
            }
            Spacer(Modifier.height(24.dp))

            teams.forEachIndexed { idx, team ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Icon(if (idx == 0) Icons.Rounded.PhoneAndroid else Icons.Rounded.Laptop, null, tint = FarmColors.Primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        FText(team.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                        FText(team.about, fontSize = 13.sp, color = FarmColors.TextMuted)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    team.members.forEach { m ->
                        val lead = m.role.lowercase().contains("lead")
                        FarmCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp), border = BorderStroke(1.dp, FarmColors.Border)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(FarmColors.PrimaryLight), contentAlignment = Alignment.Center) {
                                    Text(m.name.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FarmColors.Primary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    FText(m.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
                                    Pill(
                                        m.role,
                                        if (lead) FarmColors.WarningBg else FarmColors.Subtle,
                                        if (lead) FarmColors.Warning else FarmColors.TextMuted,
                                        Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                            FText(m.built, fontSize = 13.sp, lineHeight = 18.sp, color = FarmColors.TextBody, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(FarmColors.Subtle)
                                    .clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${m.github}")))
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Code, null, tint = FarmColors.Info, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("@${m.github}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Info)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            FText(
                "Farm Platform Prototype · Academic Group Project", variant = TextVariant.Caption,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
            )
        }
    }
}
