package app.duotrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// abceed のような「白地 × 青のアクセント × 大きな数字」を Material 3 の配色で
private val Blue = Color(0xFF3355E8)
private val BlueDark = Color(0xFF9DB0FF)
private val Orange = Color(0xFFFF9F1C)

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF00A3A3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF2F0),
    onSecondaryContainer = Color(0xFF00201F),
    tertiary = Orange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6C7),
    onTertiaryContainer = Color(0xFF3A2000),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE6E8F0),
    onSurfaceVariant = Color(0xFF5A5F6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFEAECF3),
    surfaceContainerHighest = Color(0xFFE3E6EE),
    outline = Color(0xFFC4C8D4),
    outlineVariant = Color(0xFFE1E4EC),
    error = Color(0xFFE5484D),
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF5C0A0D),
)

private val Dark = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF00218C),
    primaryContainer = Color(0xFF1F3BC2),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF5ED8D4),
    secondaryContainer = Color(0xFF004F4D),
    onSecondaryContainer = Color(0xFFCCF2F0),
    tertiary = Color(0xFFFFB95C),
    tertiaryContainer = Color(0xFF5C3700),
    onTertiaryContainer = Color(0xFFFFE6C7),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E4EA),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF2A2D36),
    onSurfaceVariant = Color(0xFFB9BDCB),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF1A1C22),
    surfaceContainer = Color(0xFF1E2027),
    surfaceContainerHigh = Color(0xFF282A32),
    surfaceContainerHighest = Color(0xFF33353D),
    outline = Color(0xFF8C90A0),
    outlineVariant = Color(0xFF3A3D47),
    error = Color(0xFFFF8A8C),
    errorContainer = Color(0xFF7A1C20),
    onErrorContainer = Color(0xFFFFE1E1),
)

/** 学習状態の色（苦手・得意・覚えた・未学習）。 */
@Immutable
data class StatusColors(val weak: Color, val good: Color, val mastered: Color, val fresh: Color, val streak: Color)

private val LightStatus = StatusColors(
    weak = Color(0xFFE5484D), good = Color(0xFF3B82F6), mastered = Color(0xFF22A06B),
    fresh = Color(0xFFCBD0DC), streak = Orange,
)
private val DarkStatus = StatusColors(
    weak = Color(0xFFFF7A7D), good = Color(0xFF7AA9FF), mastered = Color(0xFF4CD39A),
    fresh = Color(0xFF454956), streak = Color(0xFFFFB95C),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** 例文表示用（少し大きく、行間ひろめ）。 */
val SentenceStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DuoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object StatusPalette {
    val current: StatusColors
        @Composable get() = LocalStatusColors.current
}
