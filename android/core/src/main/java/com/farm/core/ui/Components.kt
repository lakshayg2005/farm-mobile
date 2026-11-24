package com.farm.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector

import com.farm.core.R

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.text.ExperimentalTextApi

import androidx.compose.ui.text.font.FontVariation

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.Font

import androidx.compose.material3.ProvideTextStyle

import androidx.compose.material3.Shapes

import androidx.compose.material3.Typography

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

/**
 * Ledger design tokens (direction C, same as the website; farm-backend docs/android-handoff.md
 * section 11). Light theme only: the tokens define no dark palette.
 */
object FarmColors {
    val Primary = Color(0xFF1D7A4D)
    val PrimaryDark = Color(0xFF155C3A)
    val PrimaryLight = Color(0xFFE3F1E9)
    val Background = Color(0xFFF2F4F5)
    val Surface = Color(0xFFFFFFFF)
    val TextDark = Color(0xFF171A1D)
    val TextBody = Color(0xFF171A1D)
    val TextMuted = Color(0xFF4B5258)
    val TextLabel = Color(0xFF4B5258)
    val Border = Color(0xFFDFE3E6)
    val InputBorder = Color(0xFFC9CFD4)
    val Subtle = Color(0xFFE6E9EB)
    val Info = Color(0xFF2F5FD0)
    val InfoBg = Color(0xFFE8EEFB)
    val Warning = Color(0xFFB7791F)
    val WarningBg = Color(0xFFFBF3E6)
    val Error = Color(0xFFC2412D)
    val ErrorBg = Color(0xFFFBEAE7)
}

/** Spacing scale (4 dp base) and radii. */
object FarmDimens {
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S6 = 24.dp
    val S8 = 32.dp
    val CardRadius = 12.dp
    val ControlRadius = 8.dp
    val SegmentRadius = 3.dp
    val TouchTarget = 48.dp
    val PrimaryButtonHeight = 50.dp
}

/** Instrument Serif for screen titles, Instrument Sans (variable, 400 to 700) for everything else; bundled, not downloaded. */
object FarmFonts {
    val Serif = FontFamily(Font(R.font.instrument_serif))

    @OptIn(ExperimentalTextApi::class)
    private fun sans(weight: FontWeight) = Font(
        R.font.instrument_sans, weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    val Sans = FontFamily(sans(FontWeight.Normal), sans(FontWeight.Medium), sans(FontWeight.SemiBold), sans(FontWeight.Bold))
}

private val FarmTypography: Typography = Typography().let { t ->
    fun TextStyle.sans() = copy(fontFamily = FarmFonts.Sans)
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = FarmFonts.Serif, fontWeight = FontWeight.Normal),
        displayMedium = t.displayMedium.copy(fontFamily = FarmFonts.Serif, fontWeight = FontWeight.Normal),
        displaySmall = t.displaySmall.copy(fontFamily = FarmFonts.Serif, fontWeight = FontWeight.Normal),
        headlineLarge = t.headlineLarge.copy(fontFamily = FarmFonts.Serif, fontWeight = FontWeight.Normal),
        headlineMedium = t.headlineMedium.copy(fontFamily = FarmFonts.Serif, fontWeight = FontWeight.Normal),
        headlineSmall = t.headlineSmall.sans(),
        titleLarge = t.titleLarge.sans(), titleMedium = t.titleMedium.sans(), titleSmall = t.titleSmall.sans(),
        bodyLarge = t.bodyLarge.sans(), bodyMedium = t.bodyMedium.sans(), bodySmall = t.bodySmall.sans(),
        labelLarge = t.labelLarge.sans(), labelMedium = t.labelMedium.sans(), labelSmall = t.labelSmall.sans(),
    )
}

/** Parses "#rrggbb" colours used by the shared status/badge tables. */
fun hex(value: String): Color = Color(android.graphics.Color.parseColor(value))

@Composable
fun FarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FarmColors.Primary,
            onPrimary = Color.White,
            primaryContainer = FarmColors.PrimaryLight,
            onPrimaryContainer = FarmColors.PrimaryDark,
            secondary = FarmColors.Info,
            onSecondary = Color.White,
            tertiary = FarmColors.Warning,
            background = FarmColors.Background,
            onBackground = FarmColors.TextDark,
            surface = FarmColors.Surface,
            onSurface = FarmColors.TextDark,
            surfaceVariant = FarmColors.Subtle,
            onSurfaceVariant = FarmColors.TextMuted,
            surfaceContainerLow = FarmColors.Surface,
            surfaceContainer = FarmColors.Surface,
            surfaceContainerHigh = FarmColors.Surface,
            outline = FarmColors.InputBorder,
            outlineVariant = FarmColors.Border,
            error = FarmColors.Error,
            onError = Color.White,
        ),
        typography = FarmTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(FarmDimens.ControlRadius),
            small = RoundedCornerShape(FarmDimens.ControlRadius),
            medium = RoundedCornerShape(FarmDimens.CardRadius),
            large = RoundedCornerShape(FarmDimens.CardRadius),
            extraLarge = RoundedCornerShape(FarmDimens.CardRadius),
        ),
    ) {
        ProvideTextStyle(FarmTypography.bodyLarge.copy(color = FarmColors.TextDark), content)
    }
}

enum class TextVariant { H1, H2, Body, Caption, Label }

private fun styleFor(v: TextVariant): TextStyle = when (v) {
    TextVariant.H1 -> TextStyle(fontFamily = FarmFonts.Serif, fontSize = 36.sp, lineHeight = 40.sp, color = FarmColors.TextDark)
    TextVariant.H2 -> TextStyle(fontFamily = FarmFonts.Sans, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
    TextVariant.Body -> TextStyle(fontFamily = FarmFonts.Sans, fontSize = 15.sp, lineHeight = 21.sp, color = FarmColors.TextBody)
    TextVariant.Caption -> TextStyle(fontFamily = FarmFonts.Sans, fontSize = 13.sp, lineHeight = 18.sp, color = FarmColors.TextMuted)
    TextVariant.Label -> TextStyle(fontFamily = FarmFonts.Sans, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextLabel)
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

/** Primary (green), Secondary (outlined), Ghost (text), Destructive (outlined, error-coloured label). */
enum class ButtonVariant { Primary, Secondary, Ghost, Destructive }

@Composable
fun FarmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(FarmDimens.ControlRadius)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val fg = when (variant) {
        ButtonVariant.Primary -> Color.White
        ButtonVariant.Destructive -> FarmColors.Error
        else -> FarmColors.Primary
    }
    val active = enabled && !loading
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = fg,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = fg,
            )
        }
    }
    val minHeight = if (variant == ButtonVariant.Primary) FarmDimens.PrimaryButtonHeight else FarmDimens.TouchTarget
    val m = modifier.alpha(if (enabled) 1f else 0.45f).heightIn(min = minHeight)
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick, enabled = active, modifier = m, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = FarmColors.Primary,
                disabledContainerColor = FarmColors.Primary,
            ),
            content = content,
        )
        ButtonVariant.Secondary, ButtonVariant.Destructive -> OutlinedButton(
            onClick = onClick, enabled = active, modifier = m, shape = shape, contentPadding = padding,
            border = BorderStroke(1.dp, if (variant == ButtonVariant.Destructive) FarmColors.Error else FarmColors.InputBorder),
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
    val shape = RoundedCornerShape(FarmDimens.CardRadius)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    @Suppress("NAME_SHADOWING") val border = border ?: BorderStroke(1.dp, FarmColors.Border)
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
                .clip(RoundedCornerShape(FarmDimens.ControlRadius))
                .background(if (enabled) Color.White else FarmColors.Subtle)
                .border(1.dp, if (error != null) FarmColors.Error else FarmColors.InputBorder, RoundedCornerShape(FarmDimens.ControlRadius))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                Text(prefix, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Primary)
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, fontSize = 15.sp, color = FarmColors.TextMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = TextStyle(fontFamily = FarmFonts.Sans, fontSize = 15.sp, color = FarmColors.TextDark),
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

/** Top app bar: white surface with a 1 dp bottom border, ink title, a 48 dp back button. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(FarmColors.Surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(SideSlot), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) {
                    Box(
                        Modifier.size(FarmDimens.TouchTarget).clip(CircleShape).clickable(onClick = onBack)
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = FarmColors.TextDark) }
                }
            }
            Text(
                title,
                color = FarmColors.TextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.heightIn(min = FarmDimens.TouchTarget).width(SideSlot), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = actions)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(FarmColors.Border))
    }
}

/** A screen's opening: the serif title (36 sp) and a one-line summary under it. */
@Composable
fun ScreenTitle(title: String, summary: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        FText(title, variant = TextVariant.H1)
        summary?.let { FText(it, variant = TextVariant.Caption, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** Status shown as a colour dot plus text, never colour alone. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(FarmColors.Surface).border(1.dp, FarmColors.Border, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.TextDark)
    }
}

private val SideSlot = 96.dp

@Composable
fun CenteredLoading(text: String? = null) {
    // A skeleton of the screen to come rather than a spinner over a blank page.
    Column(Modifier.fillMaxSize().background(FarmColors.Background)) {
        if (text != null) FText(text, variant = TextVariant.Caption, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        LoadingSkeleton(rows = 3)
    }
}

/** Small rounded label with a background colour. */
@Composable
fun Pill(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 11,
    border: Color? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size((fontSize + 3).dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** A small Material icon followed by text, in one colour (replaces emoji-prefixed labels). */
@Composable
fun IconLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FarmColors.TextLabel,
    iconColor: Color = color,
    fontSize: Int = 13,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size((fontSize + 3).dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text, color = color, fontSize = fontSize.sp, fontWeight = fontWeight, maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

/** Coloured banner for errors / success messages. */
@Composable
fun Banner(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
