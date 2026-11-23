package digital.anchorstudio.soundprint

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment as UiAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import digital.anchorstudio.soundprint.R
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Soundprint app-open animation (Jetpack Compose).
 *
 * SETUP
 * 1. Copy soundprint_logo.png into app/src/main/res/drawable/ (keep the file name).
 * 2. Put this file in your project and fix the package + R import above.
 * 3. Show it first when the app opens:
 *
 *    setContent {
 *        var showSplash by remember { mutableStateOf(true) }
 *        if (showSplash) SoundprintSplash(onFinished = { showSplash = false })
 *        else HomeScreen()
 *    }
 *
 * Timeline (seconds): the logo reveals smoothly from 0.2-1.5, glow 1.3+,
 * wordmark 2.4-3.6, then a short hold and onFinished() at ~5.5s.
 */

private const val TOTAL_SECONDS = 5f
private const val HOLD_MS = 500L

private val BgCenter = Color(0xFF2D1B5E)
private val BgEdge = Color(0xFF140D2E)
private val WordmarkBrush = Brush.horizontalGradient(
    listOf(Color(0xFF6EE0FF), Color(0xFFC58AF9), Color(0xFFF472B6))
)

private fun easeOut(p: Float): Float {
    val x = p.coerceIn(0f, 1f)
    return 1f - (1f - x).pow(3)
}

@Composable
fun SoundprintSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(context) {
        requireNotNull(context.getDrawable(R.drawable.soundprint_logo))
            .toBitmap()
            .asImageBitmap()
    }

    // Respect "remove animations" in system settings: jump straight to the finished logo.
    val animationsOff = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val clock = remember { Animatable(if (animationsOff) TOTAL_SECONDS else 0f) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        if (!animationsOff) {
            clock.animateTo(
                TOTAL_SECONDS,
                tween((TOTAL_SECONDS * 1000).toInt(), easing = LinearEasing)
            )
        }
        delay(HOLD_MS)
        finished()
    }

    val t = clock.value
    val glow = (easeOut((t - 1.3f) / 0.8f) * (0.7f + 0.3f * sin(t * 4f))).coerceIn(0f, 1f)
    val word = easeOut((t - 2.4f) / 1.2f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(BgCenter, BgEdge),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        val logoSize = minOf(maxWidth * 0.64f, maxHeight * 0.38f, 340.dp)

        Column(
            modifier = Modifier.align(UiAlignment.Center),
            horizontalAlignment = UiAlignment.CenterHorizontally
        ) {
            Box(Modifier.size(logoSize)) {
                // Soft glow layer (blur needs Android 12+; on older devices it just shows the crisp logo).
                LogoBars(
                    image, t,
                    Modifier
                        .matchParentSize()
                        .alpha(glow)
                        .blur(12.dp, BlurredEdgeTreatment.Unbounded)
                )
                LogoBars(image, t, Modifier.matchParentSize())
            }

            Spacer(Modifier.height(32.dp))

            BasicText(
                text = "soundprint",
                modifier = Modifier.graphicsLayer {
                    alpha = word
                    translationY = (1f - word) * 16.dp.toPx()
                },
                style = TextStyle(
                    brush = WordmarkBrush,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    // TODO: set fontFamily = YourBrandFont
                    letterSpacing = (0.28f - 0.30f * word).em
                )
            )
        }
    }
}

/** Reveals the complete logo upward in one draw, avoiding per-strip frame work. */
@Composable
private fun LogoBars(image: ImageBitmap, t: Float, modifier: Modifier) {
    Canvas(modifier) { drawBars(image, t) }
}

private fun DrawScope.drawBars(image: ImageBitmap, t: Float) {
    val progress = easeOut((t - 0.2f) / 1.3f)
    if (progress <= 0f) return

    val scale = size.width / image.width.toFloat()
    val sourceTop = ((1f - progress) * image.height).toInt().coerceIn(0, image.height - 1)
    val sourceHeight = image.height - sourceTop

    drawImage(
        image = image,
        srcOffset = IntOffset(0, sourceTop),
        srcSize = IntSize(image.width, sourceHeight),
        dstOffset = IntOffset(0, (sourceTop * scale).roundToInt()),
        dstSize = IntSize(
            size.width.roundToInt(),
            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        ),
        filterQuality = FilterQuality.High
    )
}
