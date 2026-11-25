package digital.anchorstudio.soundprint

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Shared first-view Pro unlock reveal. Called by ProFeatureGate so feature surfaces
 * get the same treatment without adding a new lock icon or changing feature UI.
 */
@Composable
internal fun SoundprintProUnlockReveal(
    featureKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val proUi = LocalSoundprintProUiAccess.current
    if (!proUi.entitled) {
        Box(modifier) { content() }
        return
    }

    val context = LocalContext.current
    val userScope = SoundprintProGate.currentUserId().orEmpty().ifBlank { "local" }
    val preferences = remember(context) {
        context.getSharedPreferences("soundprint_pro_feature_unlocks", android.content.Context.MODE_PRIVATE)
    }
    val stableKey = remember(userScope, featureKey) {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$userScope:$featureKey".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        "seen_$digest"
    }
    var hasPlayed by remember(stableKey) {
        mutableStateOf(preferences.getBoolean(stableKey, false))
    }
    var shouldAnimate by remember(stableKey) { mutableStateOf(false) }
    val progress = remember(stableKey) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    LaunchedEffect(shouldAnimate, stableKey) {
        if (shouldAnimate) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing)
            )
            shouldAnimate = false
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            if (hasPlayed) return@onGloballyPositioned
            val bounds = coordinates.boundsInWindow()
            val visibleHeight = (minOf(bounds.bottom, viewportHeightPx) - maxOf(bounds.top, 0f))
                .coerceAtLeast(0f)
            val requiredVisible = minOf(coordinates.size.height * 0.35f, viewportHeightPx * 0.22f)
            if (visibleHeight >= requiredVisible && visibleHeight > 0f) {
                hasPlayed = true
                // SharedPreferences.apply updates its in-memory value immediately and
                // persists asynchronously; mark before animation to avoid replay on recompose.
                preferences.edit().putBoolean(stableKey, true).apply()
                shouldAnimate = true
            }
        }
    ) {
        Box(
            Modifier.graphicsLayer {
                val p = if (shouldAnimate) progress.value else 1f
                val startScale = if (shouldAnimate) 0.985f else 1f
                scaleX = startScale + (1f - startScale) * p
                scaleY = startScale + (1f - startScale) * p
                alpha = if (shouldAnimate) 0.88f + 0.12f * p else 1f
            }
        ) {
            content()
        }

        if (shouldAnimate && progress.value < 1f) {
            val p = progress.value
            val gold = LocalSoundprintThemeTokens.current.proGold
            Canvas(Modifier.matchParentSize()) {
                val sweepWidth = size.width * 0.42f
                val sweepStart = size.width * p - sweepWidth
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, gold.copy(alpha = 0.24f), Color.Transparent),
                        startX = sweepStart,
                        endX = sweepStart + sweepWidth
                    ),
                    alpha = (1f - p).coerceIn(0f, 1f)
                )
                drawRoundRect(
                    color = gold.copy(alpha = (1f - p) * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp).size(20.dp)
            ) {
                SoundprintProSparkle(
                    modifier = Modifier.matchParentSize().graphicsLayer {
                        val pulse = (1f - p)
                        scaleX = 0.7f + pulse * 0.8f
                        scaleY = 0.7f + pulse * 0.8f
                        rotationZ = -28f + 52f * p
                        alpha = (1f - p).coerceIn(0f, 1f)
                    },
                    contentDescription = null,
                    fillColor = gold
                )
            }
        }
    }
}
