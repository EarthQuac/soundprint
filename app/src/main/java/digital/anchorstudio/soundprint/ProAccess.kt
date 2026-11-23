package digital.anchorstudio.soundprint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class SoundprintProUiAccess(
    val entitled: Boolean = false,
    val onOpenPro: () -> Unit = {}
)

internal val LocalSoundprintProUiAccess = staticCompositionLocalOf { SoundprintProUiAccess() }

@Composable
internal fun ProFeatureBadge(modifier: Modifier = Modifier) {
    val access = LocalSoundprintProUiAccess.current
    if (!access.entitled) {
        Surface(
            modifier = modifier.clip(CircleShape).clickable(enabled = !access.entitled, onClick = access.onOpenPro),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
        ) {
            Row(
                Modifier.height(20.dp).padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SoundprintIcon(SoundprintIcons.Crown, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Pro",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = .65.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

