package digital.anchorstudio.soundprint

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one switch used across Soundprint.
 *
 * Off: filled neutral track, thin outline and a light thumb that is always visible.
 * On: solid accent track with a high-contrast thumb and a check mark, so the state reads at a glance.
 * Colours follow the active palette.
 */
@Composable
internal fun SoundprintSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                SoundprintIcon(
                    SoundprintIcons.Check,
                    null,
                    Modifier.size(SwitchDefaults.IconSize),
                    tint = scheme.primary
                )
            }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = scheme.primary,
            checkedIconColor = scheme.primary,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedTrackColor = scheme.surfaceVariant,
            uncheckedBorderColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            disabledCheckedThumbColor = scheme.onPrimary.copy(alpha = 0.6f),
            disabledCheckedTrackColor = scheme.primary.copy(alpha = 0.35f),
            disabledCheckedBorderColor = scheme.primary.copy(alpha = 0.35f),
            disabledUncheckedThumbColor = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            disabledUncheckedTrackColor = scheme.surfaceVariant.copy(alpha = 0.6f),
            disabledUncheckedBorderColor = scheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    )
}
