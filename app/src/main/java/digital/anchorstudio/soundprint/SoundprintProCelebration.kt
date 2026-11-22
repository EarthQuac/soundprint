package digital.anchorstudio.soundprint

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import digital.anchorstudio.soundprint.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Soundprint Pro purchase celebration (Jetpack Compose).
 *
 * SETUP
 * 1. soundprint_logo.png must be in res/drawable/ (same file as the splash).
 * 2. Fix the package + R import above.
 * 3. Call it right after a purchase succeeds:
 *
 *    if (purchaseSucceeded) {
 *        SoundprintProCelebration(onContinue = { navController.popBackStack() })
 *    }
 *
 * Timeline (seconds): shockwave rings 0.4+, logo pops in 0.3 with an overshoot,
 * confetti burst 0.5, side confetti cannons 1.2, PRO badge 1.6, title 2.1,
 * subtitle 2.5, button 3.2. The logo bars keep bouncing until the user taps the button.
 * Two haptic hits fire on the logo pop and the badge pop.
 */

private const val STRIP_PX = 4
private const val TOTAL_SECONDS = 4.5f
private const val LOGO_CENTER_Y = 0.34f // logo center as a fraction of screen height

private val BgCenter = Color(0xFF2D1B5E)
private val BgEdge = Color(0xFF140D2E)
private val Cyan = Color(0xFF6EE0FF)
private val Lavender = Color(0xFFC58AF9)
private val Pink = Color(0xFFF472B6)
private val Gold = Color(0xFFFFD166)
private val Ink = Color(0xFFE9E4FF)
private val BrandBrush = Brush.horizontalGradient(listOf(Cyan, Lavender, Pink))
private val ConfettiColors = listOf(Cyan, Lavender, Pink, Gold, Color.White)
private val RingColors = listOf(Cyan, Pink, Lavender)

private fun easeOut(p: Float): Float {
    val x = p.coerceIn(0f, 1f)
    return 1f - (1f - x).pow(3)
}

private class Piece(
    val start: Float, val life: Float,
    val ox: Float, val oy: Float,   // origin as screen fractions
    val vx: Float, val vy: Float,   // launch velocity in screen-min-dimensions per second
    val rot0: Float, val spin: Float, val flip: Float,
    val w: Float, val h: Float,     // size in dp
    val color: Color
)

private fun makePieces(): List<Piece> {
    val r = Random(7)
    val out = ArrayList<Piece>()
    fun add(start: Float, ox: Float, oy: Float, angle: Float, speed: Float) {
        out += Piece(
            start = start, life = 2.4f + r.nextFloat() * 0.8f,
            ox = ox, oy = oy,
            vx = cos(angle) * speed, vy = sin(angle) * speed,
            rot0 = r.nextFloat() * 360f, spin = (r.nextFloat() - 0.5f) * 720f,
            flip = 4f + r.nextFloat() * 8f,
            w = 6f + r.nextFloat() * 6f, h = 10f + r.nextFloat() * 8f,
            color = ConfettiColors[r.nextInt(ConfettiColors.size)]
        )
    }
    // Main burst from the logo
    repeat(90) {
        val angle = (-PI / 2).toFloat() + (r.nextFloat() - 0.5f) * (PI * 1.7).toFloat()
        add(0.5f + r.nextFloat() * 0.1f, 0.5f, LOGO_CENTER_Y, angle, 0.7f + r.nextFloat() * 1.3f)
    }
    // Side cannons from the bottom corners
    repeat(45) {
        val a = (-PI / 2).toFloat() + 0.25f + r.nextFloat() * 0.5f
        add(1.2f + r.nextFloat() * 0.15f, 0.0f, 1.0f, a, 1.4f + r.nextFloat() * 1.0f)
    }
    repeat(45) {
        val a = (-PI / 2).toFloat() - 0.25f - r.nextFloat() * 0.5f
        add(1.2f + r.nextFloat() * 0.15f, 1.0f, 1.0f, a, 1.4f + r.nextFloat() * 1.0f)
    }
    return out
}

@Composable
fun SoundprintProCelebration(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "You're Pro",
    subtitle: String = "Every feature is unlocked.",
    buttonText: String = "Start exploring",
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val image = ImageBitmap.imageResource(R.drawable.soundprint_logo)

    val animationsOff = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val clock = remember { Animatable(if (animationsOff) TOTAL_SECONDS else 0f) }
    val pop = remember { Animatable(if (animationsOff) 1f else 0f) }
    val badgePop = remember { Animatable(if (animationsOff) 1f else 0f) }
    val pieces = remember { makePieces() }
    val phase by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase"
    )

    LaunchedEffect(Unit) {
        if (!animationsOff) clock.animateTo(
            TOTAL_SECONDS, tween((TOTAL_SECONDS * 1000).toInt(), easing = LinearEasing)
        )
    }
    LaunchedEffect(Unit) {
        if (!animationsOff) {
            delay(300)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(Unit) {
        if (!animationsOff) {
            delay(1600)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            badgePop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }

    val t = clock.value
    val glow = (easeOut((t - 0.8f) / 0.6f) * (0.65f + 0.35f * sin(phase))).coerceIn(0f, 1f)
    val bounceAmp = easeOut((t - 1.0f) / 0.6f)
    val titleA = easeOut((t - 2.1f) / 0.7f)
    val subA = easeOut((t - 2.5f) / 0.7f)
    val btnA = easeOut((t - 3.2f) / 0.6f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(BgCenter, BgEdge),
                        center = Offset(size.width / 2f, size.height * LOGO_CENTER_Y),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        val logoSize = minOf(maxWidth * 0.55f, maxHeight * 0.28f, 280.dp)

        // Shockwave rings + confetti (full screen, behind the content)
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height * LOGO_CENTER_Y)
            for (i in 0 until 3) {
                val p = (t - (0.4f + i * 0.28f)) / 1.6f
                if (p <= 0f || p >= 1f) continue
                drawCircle(
                    color = RingColors[i],
                    radius = easeOut(p) * size.maxDimension * 0.75f,
                    center = c,
                    alpha = (1f - p) * 0.55f,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            drawConfetti(pieces, t)
        }

        // Logo
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * LOGO_CENTER_Y - logoSize / 2)
                .size(logoSize)
                .graphicsLayer {
                    val s = 0.25f + 0.75f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = (pop.value * 3f).coerceIn(0f, 1f)
                }
        ) {
            LogoBars(
                image, phase, bounceAmp,
                Modifier.matchParentSize().alpha(glow).blur(24.dp, BlurredEdgeTreatment.Unbounded)
            )
            LogoBars(image, phase, bounceAmp, Modifier.matchParentSize())
        }

        // Badge + text
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * LOGO_CENTER_Y + logoSize / 2 + 28.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = "PRO",
                modifier = Modifier
                    .graphicsLayer {
                        val s = badgePop.value
                        scaleX = s
                        scaleY = s
                        alpha = s.coerceIn(0f, 1f)
                    }
                    .background(BrandBrush, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                style = TextStyle(
                    color = BgEdge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.em
                )
            )
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = title,
                modifier = Modifier.graphicsLayer {
                    alpha = titleA
                    translationY = (1f - titleA) * 14.dp.toPx()
                },
                style = TextStyle(
                    color = Ink,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                )
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = subtitle,
                modifier = Modifier.graphicsLayer {
                    alpha = subA
                    translationY = (1f - subA) * 10.dp.toPx()
                },
                style = TextStyle(
                    color = Ink.copy(alpha = 0.75f),
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            )
        }

        // Continue button
        BasicText(
            text = buttonText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .graphicsLayer {
                    alpha = btnA
                    translationY = (1f - btnA) * 12.dp.toPx()
                }
                .background(BrandBrush, RoundedCornerShape(50))
                .clickable(enabled = btnA > 0.9f, role = Role.Button, onClick = onContinue)
                .padding(horizontal = 36.dp, vertical = 14.dp),
            style = TextStyle(color = BgEdge, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun DrawScope.drawConfetti(pieces: List<Piece>, t: Float) {
    val s = minOf(size.width, size.height)
    val k = 2.5f           // air drag
    val gravity = 1.6f     // in screen-min-dimensions per second squared
    for (p in pieces) {
        val tau = t - p.start
        if (tau < 0f || tau > p.life) continue
        val drag = (1f - exp(-k * tau)) / k
        val x = p.ox * size.width + p.vx * s * drag
        val y = p.oy * size.height + p.vy * s * drag + 0.5f * gravity * s * tau * tau
        val fade = ((p.life - tau) / 0.6f).coerceIn(0f, 1f)
        val flipW = abs(cos(tau * p.flip)).coerceAtLeast(0.15f) // paper-flutter effect
        val w = p.w.dp.toPx() * flipW
        val h = p.h.dp.toPx()
        rotate(degrees = p.rot0 + p.spin * tau, pivot = Offset(x, y)) {
            drawRect(
                color = p.color,
                topLeft = Offset(x - w / 2f, y - h / 2f),
                size = Size(w, h),
                alpha = fade
            )
        }
    }
}

/** The logo as thin vertical strips; each strip bounces like an equalizer bar. */
@Composable
private fun LogoBars(image: ImageBitmap, phase: Float, amp: Float, modifier: Modifier) {
    Canvas(modifier) {
        val src = image.width
        val n = src / STRIP_PX
        val k = size.width / src
        val yc = size.height / 2f
        for (j in 0 until n) {
            val scale = 1f + 0.07f * amp * sin(phase + j * 0.45f)
            val dh = src * k * scale
            val dy = yc - dh / 2f
            drawImage(
                image = image,
                srcOffset = IntOffset(j * STRIP_PX, 0),
                srcSize = IntSize(STRIP_PX, src),
                dstOffset = IntOffset((j * STRIP_PX * k).roundToInt(), dy.roundToInt()),
                dstSize = IntSize((STRIP_PX * k).roundToInt() + 1, dh.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium
            )
        }
    }
}
