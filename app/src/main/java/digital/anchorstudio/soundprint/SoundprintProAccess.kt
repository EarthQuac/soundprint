package digital.anchorstudio.soundprint

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class SoundprintProStatus(
    val hasPro: Boolean = false,
    val source: String? = null,
    val expiresAt: String? = null,
    val isFounder: Boolean = false,
    val founderOfferEnabled: Boolean = true,
    val founderRemaining: Int = 100
)

internal object SoundprintProAccess {
    suspend fun refresh(): SoundprintProStatus = withContext(Dispatchers.IO) {
        val payload = invoke("status")
        val status = payload.optJSONObject("status") ?: error("The Pro status response was incomplete.")
        SoundprintProStatus(
            hasPro = status.optBoolean("has_pro", false),
            source = status.optString("source").takeUnless { it == "null" || it.isBlank() },
            expiresAt = status.optString("expires_at").takeUnless { it == "null" || it.isBlank() },
            isFounder = status.optBoolean("is_founder", false),
            founderOfferEnabled = status.optBoolean("founder_offer_enabled", false),
            founderRemaining = status.optInt("founder_remaining", 0).coerceAtLeast(0)
        )
    }

    suspend fun claimFounder(): JSONObject = withContext(Dispatchers.IO) {
        invoke("claim-founder").optJSONObject("result")
            ?: error("The founder claim response was incomplete.")
    }

    private fun invoke(action: String): JSONObject {
        val token = SoundprintSupabase.client.auth.currentAccessTokenOrNull()
            ?: error("Sign in to your verified Soundprint account to check Pro access.")
        val connection = (URL("${SoundprintSupabase.SUPABASE_URL}/functions/v1/soundprint-pro").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("apikey", SoundprintSupabase.PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
        }
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("action", action).toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse {
                error("The Pro service returned an unreadable response ($code).")
            }
            if (code !in 200..299) {
                val reason = json.optString("error")
                error(
                    when (reason) {
                        "verified_email_required" -> "Verify your email address before claiming Founder Access."
                        "invalid_session", "authentication_required" -> "Your session expired. Sign in again."
                        "founder_claim_unavailable" -> "Founder Access is temporarily unavailable. Please try again."
                        "status_unavailable", "service_unavailable" -> "Pro status is temporarily unavailable. Please try again."
                        else -> "The Pro request could not be completed ($code)."
                    }
                )
            }
            if (!json.optBoolean("ok", false)) error("The Pro service could not complete the request.")
            json
        } finally {
            connection.disconnect()
        }
    }
}
