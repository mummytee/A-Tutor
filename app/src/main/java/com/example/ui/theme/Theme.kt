package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val ATutorDarkColorScheme = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = OnMintPrimaryDark,
    primaryContainer = MintContainerDark,
    onPrimaryContainer = OnMintContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = OnGoldSecondaryDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = OnGoldContainerDark,
    tertiary = CoralTertiaryDark,
    onTertiary = OnCoralTertiaryDark,
    tertiaryContainer = CoralContainerDark,
    onTertiaryContainer = OnCoralContainerDark,
    background = ObsidianBackground,
    onBackground = OnObsidianBackground,
    surface = ObsidianSurface,
    onSurface = OnObsidianBackground,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = OnObsidianSurfaceVariant,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant
)

private val ATutorLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = ChalkGoldSecondary,
    onSecondary = OnChalkGoldSecondary,
    secondaryContainer = ChalkGoldContainer,
    onSecondaryContainer = OnChalkGoldContainer,
    tertiary = ChalkCoralTertiary,
    onTertiary = OnChalkCoralTertiary,
    tertiaryContainer = ChalkCoralContainer,
    onTertiaryContainer = OnChalkCoralContainer,
    background = VellumBackground,
    onBackground = OnVellumBackground,
    surface = VellumSurface,
    onSurface = OnVellumBackground,
    surfaceVariant = VellumSurfaceVariant,
    onSurfaceVariant = OnVellumSurfaceVariant,
    surfaceContainerLow = VellumSurfaceContainerLow,
    surfaceContainer = VellumSurfaceContainer,
    surfaceContainerHigh = VellumSurfaceContainerHigh,
    outline = VellumOutline,
    outlineVariant = VellumOutlineVariant
)

val ATutorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ATutorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ATutorDarkColorScheme
        else -> ATutorLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ATutorTypography,
        shapes = ATutorShapes,
        content = content
    )
}
