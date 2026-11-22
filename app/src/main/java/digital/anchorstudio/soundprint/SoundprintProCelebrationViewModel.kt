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

    /** Call only after Play Billing confirms that the purchase was acknowledged. */
    @Synchronized
    fun onPurchaseAcknowledged(purchaseToken: String) {
        val token = purchaseToken.trim()
        if (token.isNotEmpty()) recordAndShowOnce(KEY_CONSUMED_PURCHASES, "purchase:$token")
    }

    /** Call only for a newly completed Founder claim, not an already-entitled response. */
    @Synchronized
    fun onFounderAccessClaimed(userId: String) {
        val id = userId.trim()
        if (id.isNotEmpty()) recordAndShowOnce(KEY_CONSUMED_FOUNDER_CLAIMS, "founder:$id")
    }

    private fun recordAndShowOnce(preferenceKey: String, stableEventId: String) {
        val eventHash = MessageDigest.getInstance("SHA-256")
            .digest(stableEventId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        val consumed = preferences.getStringSet(preferenceKey, emptySet())
            .orEmpty()
            .toMutableSet()
        if (!consumed.add(eventHash)) return

        // Commit before displaying so rotations, process death and duplicate callbacks
        // cannot replay the celebration for the same purchase or Founder claim.
        val persisted = preferences.edit()
            .putStringSet(preferenceKey, consumed)
            .commit()
        if (persisted) showProCelebration = true
    }

    fun dismiss() {
        showProCelebration = false
    }

    private companion object {
        const val KEY_CONSUMED_FOUNDER_CLAIMS = "consumed_founder_claims"
        const val KEY_CONSUMED_PURCHASES = "consumed_purchase_tokens"
    }
}
