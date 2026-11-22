package digital.anchorstudio.soundprint

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/**
 * Owns the one-shot Pro celebration state across Activity recreation.
 *
 * Call onPurchaseAcknowledged only after Play Billing has returned a successful
 * acknowledgePurchase result. The purchase token is hashed before persistence.
 */
internal class SoundprintProCelebrationViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(
        "soundprint_pro_celebration",
        Context.MODE_PRIVATE
    )

    var showProCelebration by mutableStateOf(false)
        private set

    @Synchronized
    fun onPurchaseAcknowledged(purchaseToken: String) {
        val token = purchaseToken.trim()
        if (token.isEmpty()) return

        val tokenHash = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        val consumed = preferences.getStringSet(KEY_CONSUMED_PURCHASES, emptySet())
            .orEmpty()
            .toMutableSet()
        if (!consumed.add(tokenHash)) return

        // Persist before showing so process death or rotation cannot replay this purchase.
        val persisted = preferences.edit()
            .putStringSet(KEY_CONSUMED_PURCHASES, consumed)
            .commit()
        if (persisted) showProCelebration = true
    }

    /** Debug-only preview path. It deliberately does not consume or persist a purchase. */
    fun showDebugPreview() {
        showProCelebration = true
    }

    fun dismiss() {
        showProCelebration = false
    }

    private companion object {
        const val KEY_CONSUMED_PURCHASES = "consumed_purchase_token_hashes"
    }
}
