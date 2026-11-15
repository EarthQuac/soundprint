package digital.anchorstudio.soundprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class SoundprintImportUiProgress(
    val stage: String,
    val currentTitle: String,
    val total: Int,
    val visited: Int,
    val imported: Int,
    val updated: Int,
    val skipped: Int,
    val errors: Int,
    val waiting: Boolean = false
)

@Composable
internal fun SoundprintImportProgressBanner(
    progress: SoundprintImportUiProgress,
    modifier: Modifier = Modifier
) {
    val total = progress.total.coerceAtLeast(0)
    val visited = progress.visited.coerceIn(0, total.coerceAtLeast(progress.visited))
    val fraction = if (total > 0) (visited.toFloat() / total).coerceIn(0f, 1f) else 0f
    val added = progress.imported + progress.updated
    val headline = when {
        progress.waiting -> "Import queued"
        progress.stage == "Scanning folders" -> "Finding your music"
        progress.stage == "Preparing" -> "Getting music ready"
        else -> "Importing your music"
    }
    val detail = when {
        progress.waiting -> "Your import will start shortly"
        total > 0 -> "${progress.currentTitle.ifBlank { "Processing tracks" }} · ${progress.visited.coerceAtLeast(0)} of $total · $added added or updated"
        else -> progress.currentTitle.ifBlank { progress.stage }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        headline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        progress.stage.uppercase(),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (total > 0) {
                    Text(
                        "${(fraction * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Text(
                detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
