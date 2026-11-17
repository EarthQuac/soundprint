package digital.anchorstudio.soundprint

import com.composables.icons.lucide.R as LucideR
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Soundprint's single icon catalogue. Standard glyphs come from
 * com.composables:icons-lucide-android. EighthNotes preserves the supplied Lucide
 * vector because that exact glyph is not included in the Android pack.
 */
internal object SoundprintIcons {
    val Clock = LucideR.drawable.lucide_ic_clock
    val Plus = LucideR.drawable.lucide_ic_plus
    val X = LucideR.drawable.lucide_ic_x
    val Heart = LucideR.drawable.lucide_ic_heart
    val Search = LucideR.drawable.lucide_ic_search
    val Disc3 = LucideR.drawable.lucide_ic_disc_3
    val FileMusic = LucideR.drawable.lucide_ic_file_music
    val ChevronRight = LucideR.drawable.lucide_ic_chevron_right
    val ChevronLeft = LucideR.drawable.lucide_ic_chevron_left
    val Pencil = LucideR.drawable.lucide_ic_pencil
    val AudioWaveform = LucideR.drawable.lucide_ic_audio_waveform
    val House = LucideR.drawable.lucide_ic_house
    val Library = LucideR.drawable.lucide_ic_library
    val Music2 = LucideR.drawable.lucide_ic_music_2
    val EllipsisVertical = LucideR.drawable.lucide_ic_ellipsis_vertical
    val Play = LucideR.drawable.lucide_ic_play
    val Pause = LucideR.drawable.lucide_ic_pause
    val SkipForward = LucideR.drawable.lucide_ic_skip_forward
    val ListMusic = LucideR.drawable.lucide_ic_list_music
    val Settings = LucideR.drawable.lucide_ic_settings
    val ArrowLeft = LucideR.drawable.lucide_ic_arrow_left
    val SlidersHorizontal = LucideR.drawable.lucide_ic_sliders_horizontal
    val ArrowLeftRight = LucideR.drawable.lucide_ic_arrow_left_right
    val Shuffle = LucideR.drawable.lucide_ic_shuffle
    val SkipBack = LucideR.drawable.lucide_ic_skip_back
    val Repeat1 = LucideR.drawable.lucide_ic_repeat_1
    val Repeat = LucideR.drawable.lucide_ic_repeat
    val Copy = LucideR.drawable.lucide_ic_copy
    val ChevronUp = LucideR.drawable.lucide_ic_chevron_up
    val ChevronDown = LucideR.drawable.lucide_ic_chevron_down
    val CircleCheckBig = LucideR.drawable.lucide_ic_circle_check_big
    val Lock = LucideR.drawable.lucide_ic_lock
    val Mail = LucideR.drawable.lucide_ic_mail
    val UserRound = LucideR.drawable.lucide_ic_user_round
    val ArrowDown = LucideR.drawable.lucide_ic_arrow_down
    val ArrowUp = LucideR.drawable.lucide_ic_arrow_up
    val Trash2 = LucideR.drawable.lucide_ic_trash_2
    val Download = LucideR.drawable.lucide_ic_download
    val Save = LucideR.drawable.lucide_ic_save
    val ChartNoAxesColumnIncreasing = LucideR.drawable.lucide_ic_chart_no_axes_column_increasing
    val Headphones = LucideR.drawable.lucide_ic_headphones
    val RefreshCw = LucideR.drawable.lucide_ic_refresh_cw
    val Waves = LucideR.drawable.lucide_ic_waves
    val Zap = LucideR.drawable.lucide_ic_zap
    val History = LucideR.drawable.lucide_ic_history
    val ShieldCheck = LucideR.drawable.lucide_ic_shield_check
    val Cloud = LucideR.drawable.lucide_ic_cloud
    val Sprout = LucideR.drawable.lucide_ic_sprout
    val MoonStar = LucideR.drawable.lucide_ic_moon_star
    val Moon = LucideR.drawable.lucide_ic_moon
    val Frown = LucideR.drawable.lucide_ic_frown
    val Smile = LucideR.drawable.lucide_ic_smile
    val Flame = LucideR.drawable.lucide_ic_flame
    val PartyPopper = LucideR.drawable.lucide_ic_party_popper
    val Brain = LucideR.drawable.lucide_ic_brain
    val CarFront = LucideR.drawable.lucide_ic_car_front
    val Dumbbell = LucideR.drawable.lucide_ic_dumbbell
    val ArchiveRestore = LucideR.drawable.lucide_ic_archive_restore
    val ArrowUpDown = LucideR.drawable.lucide_ic_arrow_up_down
    val ListPlus = LucideR.drawable.lucide_ic_list_plus
    val Check = LucideR.drawable.lucide_ic_check
    val Infinity = LucideR.drawable.lucide_ic_infinity
    val Palette = LucideR.drawable.lucide_ic_palette
    val Timer = LucideR.drawable.lucide_ic_timer
    val CircleAlert = LucideR.drawable.lucide_ic_circle_alert
    val AudioLines = LucideR.drawable.lucide_ic_audio_lines
    val Tags = LucideR.drawable.lucide_ic_tags
    val Music = LucideR.drawable.lucide_ic_music
    val EighthNotes = R.drawable.lucide_ic_eighth_notes
    val User = LucideR.drawable.lucide_ic_user
    val FolderPlus = LucideR.drawable.lucide_ic_folder_plus
    val Crown = LucideR.drawable.lucide_ic_crown
    val SunMoon = LucideR.drawable.lucide_ic_sun_moon
    val Sun = LucideR.drawable.lucide_ic_sun
    val Gauge = LucideR.drawable.lucide_ic_gauge
    val Info = LucideR.drawable.lucide_ic_info
    val BookOpenText = LucideR.drawable.lucide_ic_book_open_text
    val TimerReset = LucideR.drawable.lucide_ic_timer_reset
    val Share2 = LucideR.drawable.lucide_ic_share_2
    val RotateCcw = LucideR.drawable.lucide_ic_rotate_ccw
    val Settings2 = LucideR.drawable.lucide_ic_settings_2
    val Maximize = LucideR.drawable.lucide_ic_maximize
}

@Composable
internal fun SoundprintIcon(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val painter = painterResource(icon)
    if (tint == null) {
        Icon(painter = painter, contentDescription = contentDescription, modifier = modifier)
    } else {
        Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}
