package digital.anchorstudio.soundprint

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/**
 * Owns the one-shot Founder Access celebration state across Activity recreation.
 * Successful claim IDs are hashed and persisted before the celebration is shown.
 */
internal class SoundprintProCelebrationViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(
        "soundprint_pro_celebration",
        Context.MODE_PRIVATE
    )

    var showProCelebration by mutableStateOf(false)
        private set

    @Synchronized
    fun onFounderAccessClaimed(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return

        val eventHash = MessageDigest.getInstance("SHA-256")
            .digest("founder:$id".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        val consumed = preferences.getStringSet(KEY_CONSUMED_FOUNDER_CLAIMS, emptySet())
            .orEmpty()
            .toMutableSet()
        if (!consumed.add(eventHash)) return

        // Commit before displaying: rotations, process death and duplicate claim responses
        // cannot replay the celebration for the same Founder account.
        val persisted = preferences.edit()
            .putStringSet(KEY_CONSUMED_FOUNDER_CLAIMS, consumed)
            .commit()
        if (persisted) showProCelebration = true
    }

    fun dismiss() {
        showProCelebration = false
    }

    private companion object {
        const val KEY_CONSUMED_FOUNDER_CLAIMS = "consumed_founder_claims"
    }
}
