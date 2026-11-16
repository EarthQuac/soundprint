package digital.anchorstudio.soundprint

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.postgrest.Postgrest

/**
 * The publishable key is intended for client apps. All user data is protected
 * by RLS. A service-role/secret key must never be shipped in this application.
 */
internal object SoundprintSupabase {
    const val AUTH_REDIRECT = "soundprint://auth"
    const val GOOGLE_WEB_CLIENT_ID = "653534412728-vo4dpm27c07l44oh4c5jk7uhed7vuaj7.apps.googleusercontent.com"

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = "https://qyhueaxhxvmnblztrizg.supabase.co",
            supabaseKey = "sb_publishable_aATBRPe2KsHrxoWD33o9wA_DIdp7KDP"
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "soundprint"
                host = "auth"
            }
            install(Postgrest)
        }
    }
}
