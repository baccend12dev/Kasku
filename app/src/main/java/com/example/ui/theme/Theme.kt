package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = GoldAccent,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = MintIncome,
    onTertiary = Color.White,
    tertiaryContainer = MintIncomeSoft,
    onTertiaryContainer = EmeraldDeep,
    error = CoralExpense,
    onError = Color.White,
    errorContainer = CoralExpenseSoft,
    onErrorContainer = Color(0xFF7A1D12),
    background = AlabasterBg,
    onBackground = InkPrimary,
    surface = PureSurface,
    onSurface = InkPrimary,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = HairlineOutline,
    outlineVariant = Color(0xFFE7ECE8)
)

private val DarkColors = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF042B1E),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFF5C451),
    onSecondary = Color(0xFF332200),
    secondaryContainer = Color(0xFF42310E),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = MintIncomeDark,
    onTertiary = Color(0xFF042B1E),
    tertiaryContainer = MintIncomeDarkBg,
    onTertiaryContainer = Color(0xFFA7F3D0),
    error = CoralExpenseDark,
    onError = Color(0xFF470D08),
    errorContainer = CoralExpenseDarkBg,
    onErrorContainer = Color(0xFFFECACA),
    background = PineDarkBg,
    onBackground = PineDarkText,
    surface = PineDarkSurface,
    onSurface = PineDarkText,
    surfaceVariant = PineDarkSurfaceVariant,
    onSurfaceVariant = PineDarkSubtext,
    outline = PineDarkOutline,
    outlineVariant = Color(0xFF23332C)
)

val KasKuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KasKuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KasKuTypography,
        shapes = KasKuShapes,
        content = content
    )
}
