package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = PureWhite,
    primaryContainer = DeepNavyContainer,
    onPrimaryContainer = PureWhite,
    secondary = EmeraldGreen,
    onSecondary = PureWhite,
    secondaryContainer = EmeraldTint,
    onSecondaryContainer = EmeraldOnTint,
    tertiary = AmberWarning,
    onTertiary = PureWhite,
    tertiaryContainer = AmberTint,
    onTertiaryContainer = AmberOnTint,
    error = CrimsonRed,
    onError = PureWhite,
    errorContainer = CrimsonTint,
    onErrorContainer = CrimsonOnTint,
    background = SoftLightGrey,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SoftLightGrey,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderGrey,
    outlineVariant = CardBorderGrey
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TuitionManagementTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
