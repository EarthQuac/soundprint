package digital.anchorstudio.soundprint

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment as UiAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Soundprint app-open animation (Jetpack Compose).
 *
 * Smoothness notes
 *  - The animation clock is only read inside draw / graphicsLayer lambdas, never during
 *    composition, so nothing recomposes per frame. Each frame only redraws.
 *  - The old blurred glow layer (a full-screen RenderEffect re-rendered every frame) is replaced by a
 *    cheap animated gradient glow.
 *  - The wordmark no longer animates letter spacing (that forced a text re-layout every frame).
 *  - The clock starts after two rendered frames, so the app's heavy first composition happens before the
 *    timeline begins instead of eating the start of the animation.
 *  - The system launch screen (see Theme.SoundprintStarting) is a plain matching colour with no icon, so
 *    this animation is the first and only thing the person sees.
 *
 * Timeline (seconds): bars rise 0.2-1.5, glow 1.3+, light sweep 2.0-2.9,
 * wordmark 2.4-3.6, short hold, quick fade, then onFinished().
 */

private const val STRIP_PX = 4        // width of each source strip in image pixels
private const val TOTAL_SECONDS = 5f  // animation clock length
private const val HOLD_MS = 400L      // pause on the finished logo
private const val FADE_OUT_MS = 280   // fade into the app so it doesn't pop

private val BgCenter = Color(0xFF2D1B5E)
private val BgEdge = Color(0xFF140D2E)
private val GlowCyan = Color(0xFF6EE0FF)
private val GlowPink = Color(0xFFF472B6)
private val WordmarkBrush = Brush.horizontalGradient(
    listOf(Color(0xFF6EE0FF), Color(0xFFC58AF9), Color(0xFFF472B6))
)

private fun easeOut(p: Float): Float {
    val x = p.coerceIn(0f, 1f)
    return 1f - (1f - x).pow(3)
}

private fun rnd(j: Int): Float {
    val s = sin(j * 91.3) * 43758.5453
    return (s - floor(s)).toFloat()
}

@Composable
fun SoundprintSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The animation slices a real bitmap. soundprint_logo is a vector drawable (XML), which
    // cannot be loaded as an ImageBitmap and crashed the app on launch, so use the PNG.
    val image = remember {
        runCatching {
            ImageBitmap.imageResource(context.resources, R.drawable.soundprint_logo_bitmap)
        }.getOrNull()
    }

    // Respect "remove animations" in system settings: jump straight to the finished logo.
    val animationsOff = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val clock = remember { Animatable(if (animationsOff) TOTAL_SECONDS else 0f) }
    val bgReveal = remember { Animatable(if (animationsOff) 1f else 0f) }
    val exitFade = remember { Animatable(1f) }
    val finished by rememberUpdatedState(onFinished)

    if (image == null) {
        // Never block app launch if the logo can't be loaded.
        LaunchedEffect(Unit) { finished() }
        return
    }

    // Per-strip random offsets never change, so compute them once instead of every frame.
    val stripCount = image.width / STRIP_PX
    val stripRandom = remember(stripCount) { FloatArray(stripCount) { rnd(it) } }

    LaunchedEffect(Unit) {
        if (!animationsOff) {
            // Let the first heavy frames (app content composing underneath) finish, then start the clock.
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            launch { bgReveal.animateTo(1f, tween(350)) }
            clock.animateTo(
                TOTAL_SECONDS,
                tween((TOTAL_SECONDS * 1000).toInt(), easing = LinearEasing)
            )
        }
        delay(HOLD_MS)
        exitFade.animateTo(0f, tween(FADE_OUT_MS))
        finished()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitFade.value }
            .drawBehind {
                // Starts as the flat launch-screen colour and eases into the radial gradient, so the
                // hand-off from the system launch screen has no visible jump.
                val centre = lerp(BgEdge, BgCenter, bgReveal.value)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(centre, BgEdge),
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
                // Cheap animated glow: a static gradient whose alpha follows the clock.
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val t = clock.value
                            alpha = (easeOut((t - 1.3f) / 0.8f) * (0.7f + 0.3f * sin(t * 4f))).coerceIn(0f, 1f)
                        }
                        .drawBehind {
                            val r = size.minDimension * 0.75f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(GlowCyan.copy(alpha = 0.34f), Color.Transparent),
                                    center = Offset(size.width * 0.32f, size.height * 0.5f), radius = r
                                ),
                                radius = r, center = Offset(size.width * 0.32f, size.height * 0.5f)
                            )
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(GlowPink.copy(alpha = 0.34f), Color.Transparent),
                                    center = Offset(size.width * 0.68f, size.height * 0.5f), radius = r
                                ),
                                radius = r, center = Offset(size.width * 0.68f, size.height * 0.5f)
                            )
                        }
                )
                SplashLogoBars(image, stripRandom, clock, Modifier.matchParentSize())
            }

            Spacer(Modifier.height(32.dp))

            BasicText(
                text = "soundprint",
                modifier = Modifier.graphicsLayer {
                    val word = easeOut((clock.value - 2.4f) / 1.2f)
                    alpha = word
                    translationY = (1f - word) * 16.dp.toPx()
                    val s = 1.05f - 0.05f * word
                    scaleX = s
                    scaleY = s
                },
                style = TextStyle(
                    brush = WordmarkBrush,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.02f).em
                )
            )
        }
    }
}

/** Draws the logo as thin vertical strips that rise from the bottom, equalizer style. */
@Composable
private fun SplashLogoBars(image: ImageBitmap, random: FloatArray, clock: State<Float>, modifier: Modifier) {
    // clock.value is read inside the draw lambda only, so this redraws per frame without recomposing.
    Canvas(modifier) { drawSplashBars(image, random, clock.value) }
}

private fun DrawScope.drawSplashBars(image: ImageBitmap, random: FloatArray, t: Float) {
    val src = image.width
    val n = random.size
    val k = size.width / src
    val yc = size.height / 2f
    val sweep = (t - 2f) / 0.9f * 1.3f - 0.15f

    for (j in 0 until n) {
        val p = easeOut((t - (0.2f + 0.55f * random[j])) / 0.8f)
        if (p <= 0f) continue

        val sy = (src * (1f - p)).toInt().coerceIn(0, src - 1)
        val sh = src - sy

        val xn = (j + 0.5f) / n
        val bump = exp(-((xn - sweep) / 0.07f).pow(2))
        val scale = 1f + 0.05f * bump

        val dy = yc + (sy * k - yc) * scale
        val dh = sh * k * scale

        drawImage(
            image = image,
            srcOffset = IntOffset(j * STRIP_PX, sy),
            srcSize = IntSize(STRIP_PX, sh),
            dstOffset = IntOffset((j * STRIP_PX * k).roundToInt(), dy.roundToInt()),
            dstSize = IntSize((STRIP_PX * k).roundToInt() + 1, dh.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium
        )
    }
}
