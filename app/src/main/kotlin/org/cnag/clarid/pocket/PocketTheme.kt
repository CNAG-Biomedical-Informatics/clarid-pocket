package org.cnag.clarid.pocket

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF165D62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F0ED),
    onPrimaryContainer = Color(0xFF123D3F),
    secondary = Color(0xFF475C70),
    background = Color(0xFFF5F7F9),
    surface = Color(0xFFFDFDFE),
    surfaceVariant = Color(0xFFE9EEF2),
    onSurface = Color(0xFF1B2936),
    onSurfaceVariant = Color(0xFF4F606F),
    outline = Color(0xFF71818B),
    outlineVariant = Color(0xFFD5DEE5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD0C9),
    onPrimary = Color(0xFF073638),
    primaryContainer = Color(0xFF173E40),
    onPrimaryContainer = Color(0xFFBCEBE4),
    background = Color(0xFF101820),
    surface = Color(0xFF16212B),
    surfaceVariant = Color(0xFF25333E),
    onSurface = Color(0xFFE5EDF3),
    onSurfaceVariant = Color(0xFFAFBEC9),
    outline = Color(0xFF83959F),
    outlineVariant = Color(0xFF344650),
)

// Keep even secondary labels readable; Android font scaling still applies.
private val ReadableBody = TextStyle(fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
private val ReadableLabel = ReadableBody.copy(fontWeight = FontWeight.Medium)

private val LabTypography = Typography(
    bodyLarge = ReadableBody,
    bodyMedium = ReadableBody,
    bodySmall = ReadableBody,
    labelLarge = ReadableLabel,
    labelMedium = ReadableLabel,
    labelSmall = ReadableLabel,
    titleSmall = ReadableLabel,
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = LabTypography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
