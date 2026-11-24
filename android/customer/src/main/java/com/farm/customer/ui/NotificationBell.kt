package com.farm.customer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon

import com.farm.core.ui.FarmColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.NotificationApi
import com.farm.customer.notifications.badgeText
import com.farm.core.api.load
import kotlinx.coroutines.delay

/** How often the bell asks for the unread count while it is on screen (the website uses 30 s too). */
private const val UNREAD_POLL_MS = 30_000L

/**
 * The notifications bell with the unread count. It loads the count when it appears (so coming
 * back from the inbox shows the new number) and every 30 s while visible. A failed poll keeps the
 * last number; the inbox itself shows the error.
 */
@Composable
fun NotificationBell(onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = FarmColors.Background) {
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            load { NotificationApi.unreadCount() }.onSuccess { count = it }
            delay(UNREAD_POLL_MS)
        }
    }
    val badge = badgeText(count)
    Box(
        modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (badge == null) "Notifications" else "Notifications, $count unread"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Notifications, null, tint = FarmColors.TextDark, modifier = Modifier.size(22.dp))
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp)
                    .clip(RoundedCornerShape(10.dp)).background(FarmColors.Error)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) { Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
