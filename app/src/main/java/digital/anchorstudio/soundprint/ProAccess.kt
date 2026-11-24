package digital.anchorstudio.soundprint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class SoundprintProUiAccess(
    val entitled: Boolean = false,
    val onOpenPro: () -> Unit = {}
)

internal val LocalSoundprintProUiAccess = staticCompositionLocalOf { SoundprintProUiAccess() }

@Composable
internal fun ProFeatureBadge(modifier: Modifier = Modifier) {
    val access = LocalSoundprintProUiAccess.current
    if (!access.entitled) {
        SoundprintProSparkle(
            modifier = modifier.size(16.dp),
            contentDescription = "Pro feature"
        )
    }
}

@Composable
internal fun SoundprintProSparkle(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val themeFill = MaterialTheme.colorScheme.primary
    val goldOutline = Color(0xFFE2BE68)
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

