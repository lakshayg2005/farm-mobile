package com.farm.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farm.core.api.ApiConfig
import com.farm.core.api.isOffline
import com.farm.core.api.loadErrorMessage

// The four states every screen that loads data shows (brief section 7): Loading (a skeleton),
// Content, Empty (what to do next) and Error (the server's message, Retry, and for no
// connection the server address with a way to change it).

private val SkeletonGrey = FarmColors.Subtle

/** Grey placeholder cards in the shape of a list, instead of a spinner over a blank screen. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 4) {
    Column(
        modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(SkeletonGrey))
                Spacer(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SkeletonGrey))
                Spacer(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SkeletonGrey))
            }
        }
    }
}

/** Nothing to show: say so, and what the person can do next. */
@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let {
            androidx.compose.material3.Icon(
                it, contentDescription = null, tint = FarmColors.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp).size(44.dp),
            )
        }
        FText(title, variant = TextVariant.H2, textAlign = TextAlign.Center)
        FText(text, variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (action != null) FarmButton(action, onAction, Modifier.padding(top = 12.dp), variant = ButtonVariant.Secondary)
    }
}

/**
 * A load failed. Shows the server's message (or "Cannot reach the server at <address>") with
 * Retry; when there was no answer at all, also "Change server", which edits the gateway address
 * and retries.
 */
@Composable
fun ErrorState(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier, title: String = "Something went wrong") {
    var changing by remember { mutableStateOf(false) }
    val offline = isOffline(error)
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FText(if (offline) "Cannot reach the server" else title, variant = TextVariant.H2, textAlign = TextAlign.Center)
        FText(loadErrorMessage(error), variant = TextVariant.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        FarmButton("Retry", onRetry, Modifier.padding(top = 12.dp))
        if (offline) FarmButton("Change server", { changing = true }, Modifier.padding(top = 8.dp), variant = ButtonVariant.Secondary)
    }
    if (changing) ServerAddressDialog(onDismiss = { changing = false }, onSaved = { changing = false; onRetry() })
}

/** Edit the gateway address the app talks to (kept until changed or reset). */
@Composable
fun ServerAddressDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var draft by remember { mutableStateOf(ApiConfig.baseUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server address") },
        text = {
            Column {
                FarmInput(draft, { draft = it }, label = "Gateway URL", placeholder = ApiConfig.defaultBaseUrl, keyboardType = KeyboardType.Uri)
                FText("Built-in default: ${ApiConfig.defaultBaseUrl}", variant = TextVariant.Caption, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton({ ApiConfig.setBaseUrl(draft); onSaved() }) { Text("Save and retry") } },
        dismissButton = { TextButton({ ApiConfig.setBaseUrl(null); onSaved() }) { Text("Use default") } },
    )
}
