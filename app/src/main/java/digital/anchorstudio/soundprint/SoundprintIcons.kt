package digital.anchorstudio.soundprint

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Soundprint's single icon catalogue. Every entry is supplied by
 * com.composables:icons-lucide-android; do not add Material or hand-drawn icon assets.
 */
internal object SoundprintIcons {
    val Clock = R.drawable.lucide_ic_clock
    val Plus = R.drawable.lucide_ic_plus
    val X = R.drawable.lucide_ic_x
    val Heart = R.drawable.lucide_ic_heart
    val Search = R.drawable.lucide_ic_search
    val Disc3 = R.drawable.lucide_ic_disc_3
    val FileMusic = R.drawable.lucide_ic_file_music
    val ChevronRight = R.drawable.lucide_ic_chevron_right
    val ChevronLeft = R.drawable.lucide_ic_chevron_left
    val Pencil = R.drawable.lucide_ic_pencil
    val AudioWaveform = R.drawable.lucide_ic_audio_waveform
    val House = R.drawable.lucide_ic_house
    val Library = R.drawable.lucide_ic_library
    val Music2 = R.drawable.lucide_ic_music_2
    val EllipsisVertical = R.drawable.lucide_ic_ellipsis_vertical
    val Play = R.drawable.lucide_ic_play
    val Pause = R.drawable.lucide_ic_pause
    val SkipForward = R.drawable.lucide_ic_skip_forward
    val ListMusic = R.drawable.lucide_ic_list_music
    val Settings = R.drawable.lucide_ic_settings
    val ArrowLeft = R.drawable.lucide_ic_arrow_left
    val SlidersHorizontal = R.drawable.lucide_ic_sliders_horizontal
    val ArrowLeftRight = R.drawable.lucide_ic_arrow_left_right
    val Shuffle = R.drawable.lucide_ic_shuffle
    val SkipBack = R.drawable.lucide_ic_skip_back
    val Repeat1 = R.drawable.lucide_ic_repeat_1
    val Repeat = R.drawable.lucide_ic_repeat
    val Copy = R.drawable.lucide_ic_copy
    val ChevronUp = R.drawable.lucide_ic_chevron_up
    val ChevronDown = R.drawable.lucide_ic_chevron_down
    val CircleCheckBig = R.drawable.lucide_ic_circle_check_big
    val Lock = R.drawable.lucide_ic_lock
    val Mail = R.drawable.lucide_ic_mail
    val UserRound = R.drawable.lucide_ic_user_round
    val ArrowDown = R.drawable.lucide_ic_arrow_down
    val ArrowUp = R.drawable.lucide_ic_arrow_up
    val Trash2 = R.drawable.lucide_ic_trash_2
    val Download = R.drawable.lucide_ic_download
    val Save = R.drawable.lucide_ic_save
    val ChartNoAxesColumnIncreasing = R.drawable.lucide_ic_chart_no_axes_column_increasing
    val Headphones = R.drawable.lucide_ic_headphones
    val RefreshCw = R.drawable.lucide_ic_refresh_cw
    val Waves = R.drawable.lucide_ic_waves
    val Zap = R.drawable.lucide_ic_zap
    val History = R.drawable.lucide_ic_history
    val ShieldCheck = R.drawable.lucide_ic_shield_check
    val Cloud = R.drawable.lucide_ic_cloud
    val Sprout = R.drawable.lucide_ic_sprout
    val MoonStar = R.drawable.lucide_ic_moon_star
    val Moon = R.drawable.lucide_ic_moon
    val Frown = R.drawable.lucide_ic_frown
    val Smile = R.drawable.lucide_ic_smile
    val Flame = R.drawable.lucide_ic_flame
    val PartyPopper = R.drawable.lucide_ic_party_popper
    val Brain = R.drawable.lucide_ic_brain
    val CarFront = R.drawable.lucide_ic_car_front
    val Dumbbell = R.drawable.lucide_ic_dumbbell
    val ArchiveRestore = R.drawable.lucide_ic_archive_restore
    val ArrowUpDown = R.drawable.lucide_ic_arrow_up_down
    val ListPlus = R.drawable.lucide_ic_list_plus
    val Check = R.drawable.lucide_ic_check
    val Infinity = R.drawable.lucide_ic_infinity
    val Palette = R.drawable.lucide_ic_palette
    val Timer = R.drawable.lucide_ic_timer
    val CircleAlert = R.drawable.lucide_ic_circle_alert
    val AudioLines = R.drawable.lucide_ic_audio_lines
    val Music = R.drawable.lucide_ic_music
    val User = R.drawable.lucide_ic_user
    val FolderPlus = R.drawable.lucide_ic_folder_plus
    val Crown = R.drawable.lucide_ic_crown
    val SunMoon = R.drawable.lucide_ic_sun_moon
    val Sun = R.drawable.lucide_ic_sun
    val Gauge = R.drawable.lucide_ic_gauge
    val Info = R.drawable.lucide_ic_info
    val BookOpenText = R.drawable.lucide_ic_book_open_text
    val TimerReset = R.drawable.lucide_ic_timer_reset
    val Share2 = R.drawable.lucide_ic_share_2
    val RotateCcw = R.drawable.lucide_ic_rotate_ccw
    val Settings2 = R.drawable.lucide_ic_settings_2
    val Maximize = R.drawable.lucide_ic_maximize
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
