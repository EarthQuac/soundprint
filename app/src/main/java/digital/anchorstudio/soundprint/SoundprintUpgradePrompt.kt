package digital.anchorstudio.soundprint

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * Universal Soundprint Pro upgrade prompt.
 *
 * Every locked feature in the app goes through SoundprintProGate.require("Feature name"), which
 * sets SoundprintProGate.lockedFeature. MainActivity shows this one prompt for it, so spacing,
 * typography and behaviour are identical everywhere. Inline "locked" screens use the same
 * UpgradeCardContent so they match too.
 */

private val ProGold = Color(0xFFE2BE68)

/** Short, benefit-led description for each gated feature. Unknown names fall back to a generic line. */
internal fun proFeatureBlurb(feature: String): String = when {
    feature.equals("Gapless playback", ignoreCase = true) ->
        "Remove the silence between tracks so albums and mixes flow without a break."
    feature.equals("Gapless playback & crossfade", ignoreCase = true) ->
        "Remove gaps between tracks or blend the end of one song into the next."
    feature.equals("Pro Audio Lab", ignoreCase = true) ->
        "Shape your sound with a 10-band equalizer, bass and treble controls and loudness leveling."
    feature.equals("Discover music for your mood", ignoreCase = true) ->
        "Explore your library through personalized mood and activity matching."
    feature.equals("Synced lyrics & karaoke", ignoreCase = true) ->
        "Follow timestamped lyrics line by line, with karaoke-style synced playback."
    feature.equals("Automatic song-info autofill", ignoreCase = true) ->
        "Fill in title, artist and album details automatically after a lyrics or catalog lookup."
    feature.startsWith("TwinSense", ignoreCase = true) ->
        "Surface duplicates and alternate versions with advanced on-device audio matching."
    feature.equals("Folder auto-watch", ignoreCase = true) ->
        "Watch a music folder so Soundprint can detect newly added tracks for you."
    feature.endsWith(" theme", ignoreCase = true) ->
        "Make Soundprint yours with exclusive Pro colour palettes."
    else -> "Unlock $feature along with the full Soundprint Pro suite."
}

private val upgradeBenefits = listOf(
    "Your music library and basic playback stay free",
    "One upgrade unlocks the whole Pro suite",
    "See plans and Founder Access on the Pro page"
)

/** Modal prompt. Shown by MainActivity whenever SoundprintProGate.lockedFeature is non-null. */
@Composable
internal fun SoundprintUpgradePrompt(
    feature: String,
    onExplore: () -> Unit,
    onDismiss: () -> Unit
) {
    // One short entrance: fade + slight scale. Runs on the draw layer, so it never recomposes.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Outer box centres the card when the window is wider than the card (tablets, landscape).
        BoxWithConstraints(
            Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val e = entrance.value
                        alpha = e
                        val s = 0.94f + 0.06f * e
                        scaleX = s
                        scaleY = s
                    }
            ) {
                UpgradeCardContent(
                    feature = feature,
                    primaryLabel = "Explore Pro",
                    onPrimary = onExplore,
                    modifier = Modifier
                        .heightIn(max = maxHeight * 0.9f)
                        .verticalScroll(rememberScrollState()),
                    secondaryLabel = "Not now",
                    onSecondary = onDismiss
                )
            }
        }
    }
}

/** The shared premium card. Used by the modal prompt and by inline locked screens. */
@Composable
internal fun UpgradeCardContent(
    feature: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val benefits = if (feature.equals("Discover music for your mood", ignoreCase = true)) {
        listOf(
            "Find songs in your library by mood",
            "Choose from moods like chill, focus or workout",
            "Play songs that match the mood you pick"
        )
    } else upgradeBenefits

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(
                1.dp,
                Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.55f), ProGold.copy(alpha = 0.40f))),
                shape
            )
            .drawBehind {
                // Soft accent glow behind the header.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(scheme.primary.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.85f
                    )
                )
            }
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emblem
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = 0.30f), scheme.primary.copy(alpha = 0.08f))
                    )
                )
                .border(1.dp, ProGold.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SoundprintProSparkle(Modifier.size(30.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "PRO FEATURE",
            fontSize = 11.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold,
            color = ProGold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            feature,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = scheme.onSurface
        )

        Spacer(Modifier.height(10.dp))

        Text(
            proFeatureBlurb(feature),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            benefits.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SoundprintIcon(SoundprintIcons.Check, null, Modifier.size(12.dp), tint = scheme.primary)
                    }
                    Text(
                        line,
                        modifier = Modifier.padding(start = 12.dp),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = scheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            )
        ) {
            Text(primaryLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Text(
                    secondaryLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}
