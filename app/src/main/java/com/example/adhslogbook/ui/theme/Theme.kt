package com.example.adhslogbook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FocusLogPalette {
    val Primary = Color(0xFF004D64)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF006684)
    val OnPrimaryContainer = Color(0xFFA2E1FF)
    val Secondary = Color(0xFF4B6269)
    val SecondaryContainer = Color(0xFFCEE7EF)
    val OnSecondaryContainer = Color(0xFF51686F)
    val Tertiary = Color(0xFF394664)
    val Background = Color(0xFFF8F9F9)
    val Surface = Color(0xFFF8F9F9)
    val SurfaceContainer = Color(0xFFEDEEEE)
    val SurfaceContainerLow = Color(0xFFF3F4F4)
    val SurfaceContainerHigh = Color(0xFFE7E8E8)
    val SurfaceContainerHighest = Color(0xFFE1E3E3)
    val SurfaceLowest = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFE1E3E3)
    val OnSurface = Color(0xFF191C1C)
    val OnSurfaceVariant = Color(0xFF3F484D)
    val Outline = Color(0xFF70787E)
    val OutlineVariant = Color(0xFFBFC8CD)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)
    val SuccessContainer = Color(0xFFD6F4E0)
    val OnSuccessContainer = Color(0xFF154D29)

    val EditorialBackground = Color(0xFFFAFAF5)
    val EditorialSurface = Color(0xFFFFFFFF)
    val EditorialSurfaceAlt = Color(0xFFF4F4EF)
    val EditorialInk = Color(0xFF0A0A0A)
    val EditorialInkSoft = Color(0xFF474747)
    val EditorialBorder = Color(0x14_000000)
}

private val LightColors = lightColorScheme(
    primary = FocusLogPalette.Primary,
    onPrimary = FocusLogPalette.OnPrimary,
    primaryContainer = FocusLogPalette.PrimaryContainer,
    onPrimaryContainer = FocusLogPalette.OnPrimaryContainer,
    secondary = FocusLogPalette.Secondary,
    secondaryContainer = FocusLogPalette.SecondaryContainer,
    onSecondaryContainer = FocusLogPalette.OnSecondaryContainer,
    tertiary = FocusLogPalette.Tertiary,
    background = FocusLogPalette.Background,
    onBackground = FocusLogPalette.OnSurface,
    surface = FocusLogPalette.Surface,
    onSurface = FocusLogPalette.OnSurface,
    surfaceVariant = FocusLogPalette.SurfaceVariant,
    onSurfaceVariant = FocusLogPalette.OnSurfaceVariant,
    outline = FocusLogPalette.Outline,
    outlineVariant = FocusLogPalette.OutlineVariant,
    error = FocusLogPalette.Error,
    errorContainer = FocusLogPalette.ErrorContainer,
    onErrorContainer = FocusLogPalette.OnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF87D0F2),
    onPrimary = Color(0xFF002C39),
    primaryContainer = Color(0xFF0F4D61),
    onPrimaryContainer = Color(0xFFA2E1FF),
    secondary = Color(0xFFB2CBD3),
    background = Color(0xFF111415),
    onBackground = Color(0xFFF0F1F1),
    surface = Color(0xFF111415),
    onSurface = Color(0xFFF0F1F1),
    surfaceVariant = Color(0xFF3E464B),
    onSurfaceVariant = Color(0xFFC0C8CE),
    outline = Color(0xFF899399),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Immutable
data class FocusLogSpacing(
    val unit: Dp = 8.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val page: Dp = 24.dp,
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class FocusLogEditorialTypography(
    val hero: TextStyle,
    val eyebrow: TextStyle,
    val cardTitle: TextStyle,
    val statNumber: TextStyle,
)

private val LocalSpacing = staticCompositionLocalOf { FocusLogSpacing() }
private val LocalEditorialTypography = staticCompositionLocalOf {
    FocusLogEditorialTypography(
        hero = TextStyle.Default,
        eyebrow = TextStyle.Default,
        cardTitle = TextStyle.Default,
        statNumber = TextStyle.Default,
    )
}

object FocusLogTheme {
    val spacing: FocusLogSpacing
        @Composable get() = LocalSpacing.current

    val editorialTypography: FocusLogEditorialTypography
        @Composable get() = LocalEditorialTypography.current
}

@Composable
fun ADHSLogbookTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val editorialType = FocusLogEditorialTypography(
        hero = AppTypography.displayLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 48.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1.2).sp,
            color = FocusLogPalette.EditorialInk,
        ),
        eyebrow = AppTypography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.4.sp,
            color = FocusLogPalette.Secondary,
        ),
        cardTitle = AppTypography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = FocusLogPalette.EditorialInk,
        ),
        statNumber = AppTypography.displayLarge.copy(
            fontWeight = FontWeight.Black,
            fontSize = 64.sp,
            lineHeight = 64.sp,
            letterSpacing = (-1).sp,
            color = FocusLogPalette.EditorialInk,
        ),
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSpacing provides FocusLogSpacing(),
        LocalEditorialTypography provides editorialType,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
