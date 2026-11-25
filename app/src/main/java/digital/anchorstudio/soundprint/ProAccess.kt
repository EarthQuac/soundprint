package digital.anchorstudio.soundprint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class SoundprintProUiAccess(
    val entitled: Boolean = false,
    val onOpenPro: () -> Unit = {}
)

internal val LocalSoundprintProUiAccess = staticCompositionLocalOf { SoundprintProUiAccess() }

@Composable
internal fun ProFeatureBadge(modifier: Modifier = Modifier, sparkleSize: Dp = 16.dp) {
    val access = LocalSoundprintProUiAccess.current
    val darkTheme = LocalSoundprintDarkTheme.current
    if (!access.entitled) {
        SoundprintProSparkle(
            modifier = modifier.size(sparkleSize),
            contentDescription = "Pro feature",
            fillColor = if (darkTheme) null else LocalSoundprintThemeTokens.current.proGold
        )
    }
}

@Composable
internal fun ProFeatureGate(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val access = LocalSoundprintProUiAccess.current
    if (!enabled || access.entitled) {
        Box(modifier) { content() }
    } else {
        val darkTheme = LocalSoundprintDarkTheme.current
        Box(modifier) {
            Box(Modifier.fillMaxWidth().graphicsLayer(alpha = if (darkTheme) 0.42f else 1f)) { content() }
            Box(
                Modifier.matchParentSize().clickable(onClick = access.onOpenPro),
                contentAlignment = Alignment.TopEnd
            ) {
            }
        }
    }
}

@Composable
internal fun SoundprintProSparkle(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fillColor: Color? = null
) {
    val themeFill = fillColor ?: MaterialTheme.colorScheme.primary
    val goldOutline = LocalSoundprintThemeTokens.current.proGold
    val accessibleModifier = if (contentDescription == null) modifier else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(accessibleModifier) {
        val radius = size.minDimension * 0.48f
        val waist = radius * 0.16f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val sparkle = Path().apply {
            moveTo(centerX, centerY - radius)
            quadraticTo(centerX + waist, centerY - waist, centerX + radius, centerY)
            quadraticTo(centerX + waist, centerY + waist, centerX, centerY + radius)
            quadraticTo(centerX - waist, centerY + waist, centerX - radius, centerY)
            quadraticTo(centerX - waist, centerY - waist, centerX, centerY - radius)
            close()
        }
        drawPath(sparkle, themeFill)
        drawPath(sparkle, goldOutline, style = Stroke(width = size.minDimension * 0.095f))
    }
}

