package digital.anchorstudio.soundprint

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

private fun accountFriendlyError(error: Throwable, googleSignIn: Boolean = false): String {
    val details = generateSequence(error) { it.cause }
        .flatMap { sequenceOf(it.message.orEmpty(), it.javaClass.simpleName) }
        .joinToString(" ").lowercase()

    return when {
        details.contains("invalid login credentials") || details.contains("invalid_credentials") ||
            details.contains("wrong password") ->
            "We couldn't sign you in. Check your email and password. If you haven't created a SoundPrint account yet, choose Create account."
        details.contains("email not confirmed") || details.contains("email_not_confirmed") ->
            "Please verify your email address before signing in."
        details.contains("already registered") || details.contains("user already exists") ||
            details.contains("email address is already") ->
            "An account with this email already exists. Sign in instead."
        details.contains("weak password") || details.contains("password should") ||
            details.contains("password is too") ->
            "Choose a stronger password with at least 8 characters."
        (details.contains("otp") && (details.contains("expired") || details.contains("invalid"))) ||
            details.contains("token has expired") || details.contains("invalid token") ->
            "That verification code is invalid or has expired. Request a new code and try again."
        details.contains("session has expired") ->
            "Your session has expired. Sign in again and restart account deletion."
        details.contains("rate limit") || details.contains("too many requests") ||
            details.contains("over_email_send_rate_limit") ->
            "Too many attempts. Please wait a little before trying again."
        details.contains("username") && (details.contains("duplicate") || details.contains("unique")) ->
            "That username is already taken. Choose another one."
        details.contains("unknownhost") || details.contains("connectexception") ||
            details.contains("sockettimeoutexception") || details.contains("timed out") ||
            details.contains("network is unreachable") || details.contains("no address associated") ->
            "We couldn't connect. Check your internet connection and try again."
        googleSignIn && (details.contains("developer_error") || details.contains("developererror") ||
            details.contains("configuration") || details.contains("oauth")) ->
            "Google sign-in isn't configured correctly yet. Please try again later or use email sign-in."
        googleSignIn ->
            "Google sign-in couldn't start. Check that Google Play services are up to date, then try again."
        else -> "We couldn't complete that account request. Please try again."
    }
}

@Serializable
private data class SoundprintProfileUpdate(@SerialName("display_name") val displayName: String)

@Composable
internal fun SoundprintAccountScreen(
    onBack: () -> Unit,
    resetToSignInKey: Int = 0
) {
    val supabase = remember { SoundprintSupabase.client }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var accountEmail by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var nameDraft by remember { mutableStateOf("") }
    var usernameActionsOpen by remember { mutableStateOf(false) }
    var usernameEditorOpen by remember { mutableStateOf(false) }
    var usernameSyncPending by remember { mutableStateOf(false) }
    var hasValidatedNetwork by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("sign-in") }
    var emailPasswordExpanded by remember { mutableStateOf(false) }
    var lastUsedMethod by remember { mutableStateOf("") }
    val authMethodPreferences = remember {
        context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
    }
    LaunchedEffect(Unit) {
        lastUsedMethod = authMethodPreferences.getString("last_auth_method", "").orEmpty()
    }
    fun rememberAuthMethod(method: String) {
        lastUsedMethod = method
        authMethodPreferences.edit().putString("last_auth_method", method).apply()
    }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var otpRequested by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var otpCooldownRemaining by remember { mutableIntStateOf(0) }
    var otpIsSignup by remember { mutableStateOf(false) }
    var otpIsRecovery by remember { mutableStateOf(false) }
    var resetOtpVerified by remember { mutableStateOf(false) }
    var deletionStep by remember { mutableStateOf("idle") }
    var deletionEmail by remember { mutableStateOf("") }
    var deletionCode by remember { mutableStateOf("") }
    var deletionConfirmation by remember { mutableStateOf("") }
    var deletionOriginalUserId by remember { mutableStateOf<String?>(null) }
    var signOutConfirmationOpen by remember { mutableStateOf(false) }
    val authSessionState by supabase.auth.sessionStatus.collectAsState()
    val accountSecurityPreferences = remember {
        context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
    }
    val normalizedOtpEmail = email.trim().lowercase()
    LaunchedEffect(normalizedOtpEmail) {
        while (true) {
            val deadline = if (normalizedOtpEmail.isNotBlank()) {
                accountSecurityPreferences.getLong("otp_signin_cooldown_$normalizedOtpEmail", 0L)
            } else 0L
            val remainingMillis = deadline - System.currentTimeMillis()
            otpCooldownRemaining = if (remainingMillis > 0L) {
                ((remainingMillis + 999L) / 1000L).toInt().coerceAtMost(60)
            } else 0
            delay(1_000L)
        }
    }
    fun startOtpCooldown(requestedEmail: String) {
        val normalized = requestedEmail.trim().lowercase()
        if (normalized.isBlank()) return
        accountSecurityPreferences.edit()
            .putLong("otp_signin_cooldown_$normalized", System.currentTimeMillis() + 60_000L)
            .apply()
        if (normalized == normalizedOtpEmail) otpCooldownRemaining = 60
    }
    fun isRateLimitError(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .any { cause ->
                val details = (cause.message.orEmpty() + " " + cause.javaClass.simpleName).lowercase()
                details.contains("rate limit") || details.contains("too many requests") ||
                    details.contains("over_email_send_rate_limit") || details.contains("429")
            }
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    DisposableEffect(connectivityManager) {
        fun updateNetworkState() {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
            hasValidatedNetwork = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                hasValidatedNetwork = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                hasValidatedNetwork = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            override fun onLost(network: Network) { updateNetworkState() }
        }
        updateNetworkState()
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }

    // Account feedback belongs in the app-wide notification host, never in the form layout.
    LaunchedEffect(message, messageError) {
        val feedback = message?.trim()?.takeIf { it.isNotBlank() }
        if (feedback != null) {
            when {
                messageError -> SoundprintToast.error(feedback)
                feedback.contains("signed in", ignoreCase = true) ||
                    feedback.contains("saved", ignoreCase = true) ||
                    feedback.contains("updated", ignoreCase = true) ||
                    feedback.contains("verified", ignoreCase = true) ||
                    feedback.contains("deleted", ignoreCase = true) ||
                    feedback.contains("sent", ignoreCase = true) ||
                    feedback.contains("success", ignoreCase = true) ->
                    SoundprintToast.success(feedback)
                else -> SoundprintToast.info(feedback)
            }
            message = null
        }
    }

    LaunchedEffect(resetToSignInKey) {
        if (resetToSignInKey > 0) {
            mode = "sign-in"
            otpRequested = false
            otpCode = ""
            otpIsSignup = false
            otpIsRecovery = false
            resetOtpVerified = false
            password = ""
            confirmPassword = ""
            message = null
            messageError = false
        }
    }

    suspend fun refreshAccount() {
        val user = supabase.auth.currentUserOrNull()
        if (user == null) {
            signedIn = false
            userId = null
            accountEmail = ""
            displayName = ""
            username = ""
            usernameSyncPending = false
        } else {
            signedIn = true
            userId = user.id
            accountEmail = user.email.orEmpty()
            val preferences = context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
            val pendingUsername = preferences.getString("pending_username_$userId", null).orEmpty()
            usernameSyncPending = pendingUsername.isNotBlank()
            username = pendingUsername.ifBlank {
                user.userMetadata?.get("username")?.toString()?.trim('"').orEmpty()
            }
            displayName = username.ifBlank {
                user.userMetadata?.get("display_name")?.toString()?.trim('"')
                    ?.ifBlank { null } ?: accountEmail.substringBefore("@").ifBlank { "Listener" }
            }
            nameDraft = username
        }
    }

    LaunchedEffect(authSessionState) {
        try {
            refreshAccount()
            val activeUserId = userId
            if (activeUserId != null) {
                val preferences = context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                val verifiedAtMillis = preferences.getLong("delete_verified_at_$activeUserId", 0L)
                val stillValid = verifiedAtMillis > 0L &&
                    System.currentTimeMillis() >= verifiedAtMillis &&
                    System.currentTimeMillis() - verifiedAtMillis <= 10 * 60 * 1000L
                if (stillValid) {
                    deletionOriginalUserId = activeUserId
                    deletionEmail = accountEmail
                    deletionCode = ""
                    deletionConfirmation = ""
                    deletionStep = "confirm"
                } else {
                    preferences.edit().remove("delete_verified_at_$activeUserId").apply()
                    if (deletionStep == "confirm") deletionStep = "idle"
                }
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            message = accountFriendlyError(error)
            messageError = true
        } finally { loading = false }
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                message = accountFriendlyError(error)
                messageError = true
            } finally { busy = false }
        }
    }

    suspend fun requestPasswordResetForKnownAccount(requestedEmail: String): Boolean {
        return withContext(Dispatchers.IO) {
            val payload = org.json.JSONObject().put("email", requestedEmail).toString()
            val connection = (URL("${SoundprintSupabase.SUPABASE_URL}/functions/v1/request-password-reset").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("apikey", SoundprintSupabase.PUBLISHABLE_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = runCatching { org.json.JSONObject(body) }.getOrNull()
                if (status == 429) {
                    error("Too many requests. Please wait a little before trying again.")
                }
                if (status !in 200..299) {
                    error("PASSWORD_RESET_REQUEST_FAILED")
                }
                result?.optBoolean("sent", false) == true
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun callDeleteAccountFunction(payload: String): String {
        val accessToken = supabase.auth.currentAccessTokenOrNull()
            ?: error("Your session has expired. Sign in again and restart account deletion.")
        return withContext(Dispatchers.IO) {
            val connection = (URL("${SoundprintSupabase.SUPABASE_URL}/functions/v1/delete-account").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("apikey", SoundprintSupabase.PUBLISHABLE_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    error(when (status) {
                        401, 403 -> "Your session has expired. Sign in again and restart account deletion."
                        429 -> "Too many attempts. Please wait a little before trying again."
                        else -> "We couldn't complete the account request. Please try again."
                    })
                }
                body
            } finally {
                connection.disconnect()
            }
        }
    }

    fun requestAccountDeletionCode() {
        val requestedEmail = deletionEmail.trim().lowercase()
        val currentId = userId
        if (currentId == null || requestedEmail.isBlank() || !requestedEmail.contains("@")) {
            message = "Enter the email address for the account you're signed in to."
            messageError = true
            return
        }
        if (requestedEmail != accountEmail.trim().lowercase()) {
            message = "Enter the email address associated with this SoundPrint account."
            messageError = true
            return
        }
        perform {
            supabase.auth.signInWith(OTP) {
                this.email = requestedEmail
                createUser = false
            }
            deletionEmail = requestedEmail
            deletionOriginalUserId = currentId
            deletionCode = ""
            deletionConfirmation = ""
            deletionStep = "code"
            message = "A six-digit verification code has been sent to $requestedEmail."
            messageError = false
        }
    }

    fun verifyAccountDeletionCode() {
        if (deletionCode.length != 6) {
            message = "Enter all six digits."
            messageError = true
            return
        }
        perform {
            val payload = buildJsonObject {
                put("action", "verify")
                put("email", deletionEmail)
                put("token", deletionCode)
            }.toString()
            callDeleteAccountFunction(payload)
            val verifiedUserId = userId ?: error("Sign in again to continue.")
            context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                .edit()
                .putLong("delete_verified_at_$verifiedUserId", System.currentTimeMillis())
                .apply()
            deletionOriginalUserId = verifiedUserId
            deletionCode = ""
            deletionConfirmation = ""
            deletionStep = "confirm"
            message = "Email verified. Enter SOUNDPRINT/DELETE to permanently delete your account."
            messageError = false
        }
    }

    fun resendAccountDeletionCode() {
        perform {
            supabase.auth.signInWith(OTP) {
                this.email = deletionEmail
                createUser = false
            }
            deletionCode = ""
            message = "A new six-digit verification code has been sent."
            messageError = false
        }
    }

    fun permanentlyDeleteAccount() {
        if (deletionConfirmation != "SOUNDPRINT/DELETE") {
            message = "Type SOUNDPRINT/DELETE exactly to confirm."
            messageError = true
            return
        }
        val verifiedUserId = deletionOriginalUserId
        if (verifiedUserId.isNullOrBlank() || userId != verifiedUserId) {
            message = "Your signed-in account changed. Restart account deletion and verify the correct account."
            messageError = true
            deletionStep = "idle"
            deletionCode = ""
            deletionConfirmation = ""
            deletionOriginalUserId = null
            return
        }
        perform {
            // Re-check the live Supabase session inside the operation, not only Compose state.
            val currentUserId = supabase.auth.currentUserOrNull()?.id
            if (currentUserId != verifiedUserId) {
                error("Your signed-in account changed. Restart account deletion and verify the correct account.")
            }
            val payload = buildJsonObject {
                put("action", "delete")
                put("confirmation", "SOUNDPRINT/DELETE")
            }.toString()
            val response = org.json.JSONObject(callDeleteAccountFunction(payload))
            if (!response.optBoolean("success", false)) {
                error("The account deletion service did not confirm deletion. Please try again.")
            }
            context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                .edit().remove("delete_verified_at_$verifiedUserId").apply()
            // The server has already deleted the user; local sign-out is cleanup and must not
            // turn a successful irreversible deletion into a misleading failure state.
            runCatching { supabase.auth.signOut() }
            signedIn = false
            userId = null
            accountEmail = ""
            displayName = ""
            username = ""
            usernameSyncPending = false
            context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                .edit().remove("pending_username_$verifiedUserId").apply()
            deletionStep = "idle"
            deletionEmail = ""
            deletionCode = ""
            deletionConfirmation = ""
            deletionOriginalUserId = null
            message = "Your SoundPrint account has been permanently deleted."
            messageError = false
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            busy = true
            message = "Opening Google sign-in…"
            messageError = false
            try {
                val activity = context.findActivity()
                    ?: error("Google sign-in needs an active Android screen. Close and reopen SoundPrint, then try again.")

                val rawNonce = UUID.randomUUID().toString()
                val hashedNonce = MessageDigest.getInstance("SHA-256")
                    .digest(rawNonce.toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                val googleOption = GetSignInWithGoogleOption.Builder(SoundprintSupabase.GOOGLE_WEB_CLIENT_ID)
                    .setNonce(hashedNonce)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()

                // Keep one identity flow. The old Activity-result fallback classified every
                // RESULT_CANCELED response as a user cancellation, masking provider/config errors.
                val credentialManager = CredentialManager.create(activity)
                val result = credentialManager.getCredential(context = activity, request = request)
                if (result.credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    error("Google returned an unsupported sign-in credential. Try the Google account picker again.")
                }
                val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

                supabase.auth.signInWith(IDToken) {
                    idToken = googleCredential.idToken
                    provider = Google
                    nonce = rawNonce
                }
                refreshAccount()
                rememberAuthMethod("google")
                message = "You're signed in with Google."
                messageError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: NoCredentialException) {
                message = "No eligible Google account was found on this device. Add a Google account in Android settings, then try again."
                messageError = true
            } catch (error: GetCredentialProviderConfigurationException) {
                message = "Google sign-in is unavailable on this device. Check Google Play services and try again."
                messageError = true
            } catch (error: GetCredentialException) {
                message = when (error) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException ->
                        "Google sign-in was cancelled."
                    else -> accountFriendlyError(error, googleSignIn = true)
                }
                messageError = true
            } catch (error: Exception) {
                message = accountFriendlyError(error, googleSignIn = true)
                messageError = true
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to MaterialTheme.colorScheme.primary.copy(alpha = .075f),
                .34f to MaterialTheme.colorScheme.background,
                1f to MaterialTheme.colorScheme.background
            )
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                SoundprintIcon(SoundprintIcons.ArrowLeft, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Account", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Your SoundPrint identity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (signedIn) Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), CircleShape)
            ) {
                SoundprintIcon(
                    SoundprintIcons.UserRound,
                    null,
                    Modifier.padding(10.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (loading) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Checking your account…", Modifier.padding(top = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (signedIn && !resetOtpVerified && username.isBlank()) {
                AccountIntroPanel(
                    title = "Choose your username",
                    description = "You're signed in. Set the username SoundPrint will use to address you. You can change it later.",
                    icon = SoundprintIcons.UserRound
                )
                AccountPanel {
                    Text("YOUR USERNAME", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        leadingIcon = { SoundprintIcon(SoundprintIcons.UserRound, null) },
                        supportingText = { Text("3–20 characters · letters, numbers and underscores") },
                        singleLine = true,
                                colors = soundprintOutlinedFieldColors()
                    )
                    Button(
                        onClick = {
                            val clean = nameDraft.trim()
                            if (!clean.matches(Regex("[a-z0-9_]{3,20}"))) {
                                message = "Choose 3–20 characters using letters, numbers or underscores."
                                messageError = true
                            } else perform {
                                supabase.auth.updateUser { data { put("username", clean) } }
                                username = clean
                                displayName = clean
                                nameDraft = clean
                                message = "Username saved. Welcome to SoundPrint, @$clean."
                                messageError = false
                            }
                        },
                        enabled = !busy && nameDraft.matches(Regex("[a-z0-9_]{3,20}")),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Saving…" else "Continue") }
                }
                TextButton(
                    onClick = { signOutConfirmationOpen = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sign out") }
            } else if (signedIn && !resetOtpVerified) {
                AccountPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(58.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = .30f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                                )))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(username.firstOrNull()?.uppercase() ?: "S", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .11f),
                                shape = CircleShape
                            ) {
                                Text("ACCOUNT CONNECTED", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { usernameActionsOpen = true }, contentPadding = PaddingValues(0.dp), enabled = !busy) {
                                Text("@$username", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                Spacer(Modifier.width(6.dp))
                                Text("✎", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(accountEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            if (usernameSyncPending) {
                                Text("Saved on this device · waiting to sync", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .42f))
                    Text("Your music files and playback library stay on this device unless you choose to sync supported data.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (deletionStep == "idle") {
                    AccountPanel {
                        Text("SENSITIVE", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Delete account", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Permanently delete your SoundPrint account and its cloud profile. This cannot be undone. Music files and your local library stored on this device are not deleted.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                deletionEmail = accountEmail
                                deletionOriginalUserId = userId
                                deletionCode = ""
                                deletionConfirmation = ""
                                userId?.let { activeUserId ->
                                    context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                                        .edit().remove("delete_verified_at_$activeUserId").apply()
                                }
                                deletionStep = "email"
                                message = null
                                messageError = false
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                        ) { Text("Request account deletion") }
                    }
                } else {
                    AccountPanel {
                        Text("SENSITIVE · DELETE ACCOUNT", fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(
                            when (deletionStep) {
                                "email" -> "Verify your email"
                                "code" -> "Enter your verification code"
                                else -> "Confirm permanent deletion"
                            },
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                        )
                        if (deletionStep == "confirm") {
                            Text(
                                buildAnnotatedString {
                                    append("Your email is verified. To permanently delete this account, enter ")
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                                        append("SOUNDPRINT/DELETE")
                                    }
                                    append(" below. This action cannot be undone.")
                                },
                                fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "If this account has Founder Access, deleting it will release its Founder spot back into the available pool. Another eligible listener can claim it after creating an account and verifying their email.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                if (deletionStep == "email")
                                    "Enter the email address linked to this SoundPrint account. We'll send a six-digit security code before continuing."
                                else
                                    "Enter the six-digit code sent to $deletionEmail. You can paste the full code into the boxes.",
                                fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (deletionStep == "email") {
                            OutlinedTextField(
                                value = deletionEmail,
                                onValueChange = { deletionEmail = it.trim() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Account email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                colors = soundprintOutlinedFieldColors()
                            )
                            Button(
                                onClick = { requestAccountDeletionCode() },
                                enabled = !busy && deletionEmail.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (busy) "Sending code…" else "Send verification code") }
                        }
                        if (deletionStep == "code") {
                            EmailOtpCodeEntry(code = deletionCode, onCodeChange = { deletionCode = it })
                            Button(
                                onClick = { verifyAccountDeletionCode() },
                                enabled = !busy && deletionCode.length == 6,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (busy) "Verifying…" else "Verify email") }
                            TextButton(onClick = { resendAccountDeletionCode() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Resend code")
                            }
                        }
                        if (deletionStep == "confirm") {
                            OutlinedTextField(
                                value = deletionConfirmation,
                                onValueChange = { deletionConfirmation = it.uppercase() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Confirmation phrase") },
                                placeholder = { Text("SOUNDPRINT/DELETE", color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                singleLine = true,
                                colors = soundprintOutlinedFieldColors()
                            )
                            Button(
                                onClick = { permanentlyDeleteAccount() },
                                enabled = !busy && deletionConfirmation == "SOUNDPRINT/DELETE",
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                            ) { Text(if (busy) "Deleting account…" else "Permanently delete account") }
                        }
                        TextButton(
                            onClick = {
                                userId?.let { activeUserId ->
                                    context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                                        .edit().remove("delete_verified_at_$activeUserId").apply()
                                }
                                deletionStep = "idle"
                                deletionCode = ""
                                deletionConfirmation = ""
                                message = null
                                messageError = false
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel") }
                    }
                }
                TextButton(onClick = { signOutConfirmationOpen = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            } else {
                if (resetOtpVerified) {
                    AccountPanel {
                        Text("Choose a new password", fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Your email has been verified. Set a new password for your SoundPrint account.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(),
                        label = { Text("New password") },
                        leadingIcon = { SoundprintIcon(SoundprintIcons.Lock, null) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } },
                        singleLine = true,
                                colors = soundprintOutlinedFieldColors()
                    )
                    OutlinedTextField(
                        confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(),
                        label = { Text("Confirm new password") },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Text(if (confirmPasswordVisible) "Hide" else "Show") } },
                        singleLine = true,
                                colors = soundprintOutlinedFieldColors()
                    )
                    Button(
                        onClick = {
                            if (password.length < 8 || password != confirmPassword) {
                                message = "Use at least 8 characters and make sure both passwords match."
                                messageError = true
                            } else perform {
                                supabase.auth.updateUser { this.password = password }
                                supabase.auth.signOut()
                                resetOtpVerified = false
                                otpIsRecovery = false
                                mode = "sign-in"
                                password = ""
                                confirmPassword = ""
                                otpRequested = false
                                otpCode = ""
                                message = "Password reset successfully. You can now sign in."
                                messageError = false
                            }
                        },
                        enabled = !busy && password.isNotBlank() && confirmPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save new password") }
                    TextButton(
                        onClick = {
                            resetOtpVerified = false
                            otpIsRecovery = false
                            mode = "sign-in"
                            password = ""
                            confirmPassword = ""
                            message = null
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back to sign in") }
                } else {
                AccountIntroPanel(
                    title = when (mode) {
                        "sign-up" -> "Create your account"
                        "forgot" -> "Reset your password"
                        else -> "Sign in to SoundPrint"
                    },
                    description = when (mode) {
                        "sign-up" -> "Create an account to keep your SoundPrint identity available across devices."
                        "forgot" -> "We'll email you a six-digit recovery code to verify before choosing a new password."
                        else -> "Sign in to manage your account. Your music library stays on this device by default."
                    },
                    icon = if (mode == "forgot") SoundprintIcons.Lock else SoundprintIcons.UserRound
                )
                if (mode != "forgot") {
                    val googleLastUsed = lastUsedMethod == "google"
                    OutlinedButton(
                        onClick = { signInWithGoogle() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("G", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF4285F4))
                        Spacer(Modifier.width(12.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                        if (googleLastUsed) {
                            Spacer(Modifier.weight(1f))
                            Text("Last used", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
                        Text("OR", Modifier.padding(horizontal = 14.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
                    }
                    OutlinedButton(
                        onClick = {
                            emailPasswordExpanded = !emailPasswordExpanded
                            if (emailPasswordExpanded) mode = "sign-in"
                            message = null
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        SoundprintIcon(SoundprintIcons.Mail, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Continue with email and password", fontWeight = FontWeight.SemiBold)
                        if (lastUsedMethod == "email") {
                            Spacer(Modifier.weight(1f))
                            Text("Last used", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (mode == "forgot" || emailPasswordExpanded) {
                    OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Email address") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Mail, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, enabled = !busy && !otpRequested,
                        colors = soundprintOutlinedFieldColors()
                    )
                }
                if (emailPasswordExpanded && (mode == "sign-up" || (mode == "sign-in" && !otpRequested))) OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Lock, null) }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } }, singleLine = true,
                    colors = soundprintOutlinedFieldColors()
                )
                if (emailPasswordExpanded && mode == "sign-up") OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Text(if (confirmPasswordVisible) "Hide" else "Show") } }, singleLine = true,
                    colors = soundprintOutlinedFieldColors()
                )
                if (otpRequested) {
                    Text(
                        if (otpIsRecovery) "Enter the 6-digit password reset code sent to ${email.trim()}"
                        else "Enter the 6-digit code sent to ${email.trim()}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EmailOtpCodeEntry(code = otpCode, onCodeChange = { otpCode = it })
                    Button(onClick = {
                        if (otpCode.length != 6) { message = "Enter all six digits."; messageError = true }
                        else perform {
                            supabase.auth.verifyEmailOtp(
                                type = when {
                                    otpIsRecovery -> OtpType.Email.RECOVERY
                                    otpIsSignup -> OtpType.Email.SIGNUP
                                    else -> OtpType.Email.EMAIL
                                },
                                email = email.trim(), token = otpCode
                            )
                            otpRequested = false
                            otpCode = ""
                            if (otpIsRecovery) {
                                resetOtpVerified = true
                                message = "Code verified. Choose a new password below."
                            } else {
                                refreshAccount()
                                rememberAuthMethod("email")
                                message = "Email verified. You're signed in."
                                password = ""
                                confirmPassword = ""
                            }
                            messageError = false
                        }
                    }, enabled = !busy && otpCode.length == 6, modifier = Modifier.fillMaxWidth()) {
                        Text(if (otpIsRecovery) "Verify reset code" else "Verify email")
                    }
                    TextButton(
                        onClick = {
                            perform {
                                try {
                                    when {
                                        otpIsRecovery -> {
                                            val sent = requestPasswordResetForKnownAccount(email.trim().lowercase())
                                            if (!sent) error("Your account could not be found. Check the email address and try again.")
                                        }
                                        otpIsSignup -> supabase.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
                                        else -> supabase.auth.signInWith(OTP) {
                                            this.email = email.trim()
                                            createUser = false
                                        }
                                    }
                                    if (!otpIsRecovery && !otpIsSignup) startOtpCooldown(email)
                                    otpCode = ""
                                    message = "A new verification code has been sent."
                                    messageError = false
                                } catch (error: Exception) {
                                    if (!otpIsRecovery && !otpIsSignup && isRateLimitError(error)) {
                                        startOtpCooldown(email)
                                        message = "Please wait 60 seconds before requesting another sign-in code."
                                        messageError = true
                                    } else throw error
                                }
                            }
                        },
                        enabled = !busy && (otpIsRecovery || otpIsSignup || otpCooldownRemaining == 0),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f)
                        )
                    ) {
                        Text(
                            when {
                                otpIsRecovery -> "Resend reset code"
                                otpIsSignup -> "Resend code"
                                otpCooldownRemaining > 0 -> "Resend code · ${otpCooldownRemaining}s"
                                else -> "Resend code"
                            }
                        )
                    }
                }
                if (!otpRequested && (mode == "forgot" || emailPasswordExpanded)) Button(
                    onClick = {
                        when (mode) {
                            "forgot" -> if (!email.contains("@")) {
                                message = "Enter the email address on your account."
                                messageError = true
                            } else perform {
                                val requestedEmail = email.trim().lowercase()
                                val resetEmailSent = requestPasswordResetForKnownAccount(requestedEmail)
                                otpIsRecovery = true
                                otpIsSignup = false
                                resetOtpVerified = false
                                otpRequested = resetEmailSent
                                otpCode = ""
                                password = ""
                                confirmPassword = ""
                                if (resetEmailSent) {
                                    message = "If an account exists for $requestedEmail, a password recovery code will be sent. Enter it below if you receive one."
                                    messageError = false
                                } else {
                                    otpRequested = false
                                    message = "If an account exists for $requestedEmail, a password recovery code will be sent. Check your email and try again shortly."
                                    messageError = true
                                }
                            }
                            "sign-up" -> if (!email.contains("@") || password.length < 8 || password != confirmPassword) {
                                message = "Enter a valid email, matching passwords and at least 8 password characters."
                                messageError = true
                            } else perform {
                                supabase.auth.signUpWith(Email, redirectUrl = SoundprintSupabase.AUTH_REDIRECT) {
                                    this.email = email.trim()
                                    this.password = password
                                }
                                otpIsSignup = true
                                otpRequested = true
                                otpCode = ""
                                message = "Verification code sent. Enter it below to finish creating your account."
                                messageError = false
                            }
                            else -> if (!email.contains("@")) {
                                message = "Enter your email address."
                                messageError = true
                            } else if (password.isBlank()) {
                                message = "Enter your password, or choose email sign-in code."
                                messageError = true
                            } else perform {
                                supabase.auth.signInWith(Email) {
                                    this.email = email.trim()
                                    this.password = password
                                }
                                refreshAccount()
                                rememberAuthMethod("email")
                                password = ""
                                message = "You're signed in."
                                messageError = false
                            }
                        }
                    },
                    enabled = !busy && email.isNotBlank() && (mode == "forgot" || password.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(when (mode) { "sign-up" -> "Create account"; "forgot" -> "Send recovery code"; else -> "Sign in" })
                }
                if (mode == "sign-in" && emailPasswordExpanded && !otpRequested) TextButton(
                    onClick = {
                        if (!email.contains("@")) {
                            message = "Enter your email address."
                            messageError = true
                        } else perform {
                            try {
                                supabase.auth.signInWith(OTP) {
                                    this.email = email.trim()
                                    createUser = false
                                }
                                startOtpCooldown(email)
                                otpIsSignup = false
                                otpIsRecovery = false
                                otpRequested = true
                                otpCode = ""
                                message = "Sign-in code sent. Enter it below to sign in."
                                messageError = false
                            } catch (error: Exception) {
                                if (isRateLimitError(error)) {
                                    startOtpCooldown(email)
                                    message = "Please wait 60 seconds before requesting another sign-in code."
                                    messageError = true
                                } else throw error
                            }
                        }
                    },
                    enabled = !busy && email.isNotBlank() && otpCooldownRemaining == 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f)
                    )
                ) {
                    Text(
                        if (otpCooldownRemaining > 0) "Request sign-in code · ${otpCooldownRemaining}s"
                        else "Email me a sign-in code"
                    )
                }
                if (emailPasswordExpanded && mode != "forgot" && !otpRequested) TextButton(onClick = { mode = "forgot"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
                if (emailPasswordExpanded && !otpRequested) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mode == "sign-up") "Already have an account?" else "New to SoundPrint?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { mode = if (mode == "sign-up") "sign-in" else "sign-up"; message = null }) { Text(if (mode == "sign-up") "Sign in" else "Create account") }
                }
                if (otpRequested) TextButton(onClick = { otpRequested = false; otpCode = ""; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                if (mode == "forgot") TextButton(onClick = {
                    mode = "sign-in"
                    otpRequested = false
                    otpCode = ""
                    otpIsRecovery = false
                    otpIsSignup = false
                    resetOtpVerified = false
                    password = ""
                    confirmPassword = ""
                    message = null
                    messageError = false
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
                }
            }
            if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Working securely…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (usernameActionsOpen) AlertDialog(
        onDismissRequest = { usernameActionsOpen = false },
        title = { Text("Username") },
        text = { Text("Would you like to change your SoundPrint username?") },
        confirmButton = {
            TextButton(onClick = {
                usernameActionsOpen = false
                nameDraft = username
                usernameEditorOpen = true
            }) { Text("Edit my username") }
        },
        dismissButton = { TextButton(onClick = { usernameActionsOpen = false }) { Text("Cancel") } }
    )
    if (usernameEditorOpen) AlertDialog(
        onDismissRequest = { if (!busy) usernameEditorOpen = false },
        title = { Text("Edit my username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    supportingText = { Text("3–20 characters · letters, numbers and underscores") },
                    singleLine = true,
                                colors = soundprintOutlinedFieldColors()
                )
                if (!hasValidatedNetwork) {
                    Text("You're offline. Your change will be saved on this device and synced to Supabase when you're back online.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = nameDraft.trim()
                val id = userId
                if (!clean.matches(Regex("[a-z0-9_]{3,20}")) || id == null) {
                    message = "Choose a username with 3–20 letters, numbers or underscores."
                    messageError = true
                } else if (!hasValidatedNetwork) {
                    context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                        .edit().putString("pending_username_$id", clean).apply()
                    username = clean
                    displayName = clean
                    usernameSyncPending = true
                    usernameEditorOpen = false
                    message = "Username change queued on this device. It will sync to Supabase when you're back online."
                    messageError = false
                } else {
                    perform {
                        supabase.auth.updateUser { data { put("username", clean); put("display_name", clean) } }
                        supabase.from("account_profiles").update(SoundprintProfileUpdate(clean)) {
                            filter { eq("id", id) }
                        }
                        context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
                            .edit().remove("pending_username_$id").apply()
                        username = clean
                        displayName = clean
                        usernameSyncPending = false
                        usernameEditorOpen = false
                        message = "Username updated."
                        messageError = false
                    }
                }
            }, enabled = !busy && nameDraft.matches(Regex("[a-z0-9_]{3,20}"))) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = { usernameEditorOpen = false }, enabled = !busy) { Text("Cancel") } }
    )
    if (usernameSyncPending && hasValidatedNetwork && signedIn && userId != null) {
        LaunchedEffect(userId, hasValidatedNetwork, usernameSyncPending) {
            val id = userId ?: return@LaunchedEffect
            val preferences = context.getSharedPreferences("soundprint_account_security", Context.MODE_PRIVATE)
            val pending = preferences.getString("pending_username_$id", null)?.trim().orEmpty()
            if (pending.isNotBlank()) {
                try {
                    supabase.auth.updateUser { data { put("username", pending); put("display_name", pending) } }
                    supabase.from("account_profiles").update(SoundprintProfileUpdate(pending)) {
                        filter { eq("id", id) }
                    }
                    preferences.edit().remove("pending_username_$id").apply()
                    username = pending
                    displayName = pending
                    usernameSyncPending = false
                    message = "Your username has synced to Supabase."
                    messageError = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    message = accountFriendlyError(error)
                    messageError = true
                }
            }
        }
    }
    if (signOutConfirmationOpen) AlertDialog(
        onDismissRequest = { signOutConfirmationOpen = false },
        title = { Text("Sign out of SoundPrint?") },
        text = { Text("You'll be signed out of your SoundPrint account on this device. Your music files and local library will remain on this device.") },
        confirmButton = {
            TextButton(
                onClick = {
                    signOutConfirmationOpen = false
                    perform {
                        supabase.auth.signOut()
                        refreshAccount()
                        message = "Signed out."
                        messageError = false
                    }
                },
                enabled = !busy
            ) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { signOutConfirmationOpen = false }, enabled = !busy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AccountPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (LocalSoundprintDarkTheme.current) {
                    Modifier.shadow(5.dp, shape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .08f))
                } else {
                    Modifier.shadow(12.dp, shape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = .06f), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = .06f))
                }
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .72f), shape)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) { content(this) }
}

@Composable
private fun AccountIntroPanel(title: String, description: String, icon: Int) {
    AccountPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .13f),
                modifier = Modifier.size(54.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f), RoundedCornerShape(18.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SoundprintIcon(icon, null, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("SOUNDPRINT ACCOUNT", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(title, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(description, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


@Composable
private fun EmailOtpCodeEntry(code: String, onCodeChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = code,
        onValueChange = { incoming -> onCodeChange(incoming.filter(Char::isDigit).take(6)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        Box(
                            modifier = Modifier.weight(1f).height(58.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .border(
                                    width = 1.5.dp,
                                    color = if (code.getOrNull(index) != null) colors.primary
                                    else colors.primary.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                code.getOrNull(index)?.toString().orEmpty(),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }
                    }
                }
                Box(Modifier.matchParentSize()) { innerTextField() }
            }
        }
    )
}
