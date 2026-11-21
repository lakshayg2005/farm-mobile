package com.farm.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FarmColors {
    val Primary = Color(0xFF2D6A4F)
    val PrimaryDark = Color(0xFF1B4332)
    val PrimaryLight = Color(0xFFE8F5E9)
    val Background = Color(0xFFF5F5F0)
    val TextDark = Color(0xFF1B1B1B)
    val TextBody = Color(0xFF333333)
    val TextMuted = Color(0xFF777777)
    val TextLabel = Color(0xFF555555)
    val Border = Color(0xFFE0E0E0)
    val Error = Color(0xFFD90429)
    val ErrorBg = Color(0xFFFFEBEE)
}

/** Parses "#rrggbb" colours used by the shared status/badge tables. */
fun hex(value: String): Color = Color(android.graphics.Color.parseColor(value))

@Composable
fun FarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FarmColors.Primary,
            onPrimary = Color.White,
            secondary = FarmColors.Primary,
            background = FarmColors.Background,
            surface = Color.White,
            error = FarmColors.Error,
        ),
        content = content,
    )
}

enum class TextVariant { H1, H2, Body, Caption, Label }

private fun styleFor(v: TextVariant): TextStyle = when (v) {
    TextVariant.H1 -> TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
    TextVariant.H2 -> TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
    TextVariant.Body -> TextStyle(fontSize = 15.sp, color = FarmColors.TextBody)
    TextVariant.Caption -> TextStyle(fontSize = 12.sp, color = FarmColors.TextMuted)
    TextVariant.Label -> TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FarmColors.TextLabel)
}

@Composable
fun FText(
    text: String,
    modifier: Modifier = Modifier,
    variant: TextVariant = TextVariant.Body,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    style: TextStyle = TextStyle.Default,
) {
    Text(
        text = text,
        modifier = modifier,
        style = styleFor(variant).merge(style),
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

enum class ButtonVariant { Primary, Secondary, Ghost }

@Composable
fun FarmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val active = enabled && !loading
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = if (variant == ButtonVariant.Primary) Color.White else FarmColors.Primary,
            )
        } else {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = if (variant == ButtonVariant.Primary) Color.White else FarmColors.Primary,
            )
        }
    }
    val m = modifier.alpha(if (enabled) 1f else 0.45f).heightIn(min = 46.dp)
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick, enabled = active, modifier = m, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = FarmColors.Primary,
                disabledContainerColor = FarmColors.Primary,
            ),
            content = content,
        )
        ButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick, enabled = active, modifier = m, shape = shape, contentPadding = padding,
            border = BorderStroke(1.5.dp, FarmColors.Primary),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, disabledContainerColor = Color.White),
            content = content,
        )
        ButtonVariant.Ghost -> TextButton(
            onClick = onClick, enabled = active, modifier = m, shape = shape, contentPadding = padding,
            content = content,
        )
    }
}

@Composable
fun FarmCard(
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    border: BorderStroke? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = background)
    val shape = RoundedCornerShape(14.dp)
    val elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun FarmInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    maxLength: Int? = null,
    enabled: Boolean = true,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier.padding(vertical = 6.dp)) {
        if (label != null) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextBody)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) Color.White else Color(0xFFF5F5F5))
                .border(1.5.dp, if (error != null) FarmColors.Error else FarmColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                Text(prefix, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, fontSize = 15.sp, color = Color(0xFF999999))
                }
                BasicTextField(
                    value = value,
                    onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = TextStyle(fontSize = 15.sp, color = FarmColors.TextDark),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error != null) {
            Text(error, fontSize = 12.sp, color = FarmColors.Error, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** A row of digit boxes backed by one text field (paste and backspace work naturally). */
@Composable
fun OtpInput(value: String, onChange: (String) -> Unit, length: Int = 6, enabled: Boolean = true) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(length)) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { i ->
                    val digit = value.getOrNull(i)?.toString() ?: ""
                    val focused = enabled && (digit.isNotEmpty() || i == value.length)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (digit.isNotEmpty()) Color(0xFFF6FBF7) else Color.White)
                            .border(
                                if (focused) 2.dp else 1.5.dp,
                                if (focused || digit.isNotEmpty()) FarmColors.Primary else Color(0xFFD0D0D0),
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FarmColors.TextDark)
                    }
                }
            }
        },
    )
}

/** Green top bar with a back arrow, used by most screens. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FarmColors.Primary)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SideSlot)) {
            if (onBack != null) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.heightIn(min = 36.dp).width(SideSlot), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = actions)
    }
}

private val SideSlot = 96.dp

@Composable
fun CenteredLoading(text: String? = null) {
    Column(
        Modifier.fillMaxSize().background(FarmColors.Background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = FarmColors.Primary)
        if (text != null) FText(text, variant = TextVariant.Caption, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Small rounded label with a background colour. */
@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, fontSize: Int = 11, border: Color? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** Coloured banner for errors / success messages. */
@Composable
fun Banner(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, borderColor: Color? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
