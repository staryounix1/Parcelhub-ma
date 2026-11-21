package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val ParcelHubLightScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = IndigoTransit,
    onTertiary = Color.White,
    tertiaryContainer = IndigoContainer,
    onTertiaryContainer = OnIndigoContainer,
    error = CoralRetour,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
    background = WarmCanvasLight,
    onBackground = TextPrimaryLight,
    surface = CardSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight
)

private val ParcelHubDarkScheme = darkColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3822),
    onPrimaryContainer = EmeraldContainer,
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF2B1300),
    secondaryContainer = Color(0xFF452400),
    onSecondaryContainer = AmberContainer,
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF082047),
    tertiaryContainer = Color(0xFF172F5B),
    onTertiaryContainer = IndigoContainer,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = CoralContainer,
    background = DarkCanvas,
    onBackground = TextPrimaryDark,
    surface = DarkCardSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkOutline
)

val ParcelHubShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun ParcelHubTheme(
    language: AppLanguage = AppLanguage.AR,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ParcelHubDarkScheme else ParcelHubLightScheme
    val layoutDirection = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = parcelHubTypography(isArabic = language == AppLanguage.AR)

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppLanguage provides language
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = ParcelHubShapes,
            content = content
        )
    }
}
