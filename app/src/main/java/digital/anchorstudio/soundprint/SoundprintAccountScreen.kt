package digital.anchorstudio.soundprint

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("sign-in") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    var otpRequested by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var otpIsSignup by remember { mutableStateOf(false) }
    var otpIsRecovery by remember { mutableStateOf(false) }
    var resetOtpVerified by remember { mutableStateOf(false) }
    var deletionStep by remember { mutableStateOf("idle") }
    var deletionEmail by remember { mutableStateOf("") }
    var deletionCode by remember { mutableStateOf("") }
    var deletionConfirmation by remember { mutableStateOf("") }
    var deletionOriginalUserId by remember { mutableStateOf<String?>(null) }
    val authSessionState by supabase.auth.sessionStatus.collectAsState()

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
        } else {
            signedIn = true
            userId = user.id
            accountEmail = user.email.orEmpty()
            username = user.userMetadata?.get("username")?.toString()?.trim('"').orEmpty()
            displayName = user.userMetadata?.get("display_name")?.toString()?.trim('"')
                ?.ifBlank { null } ?: username.ifBlank { accountEmail.substringBefore("@").ifBlank { "Listener" } }
            nameDraft = username
        }
    }

    // Fallback for Android builds that expose no usable Credential Manager provider.
    val googleFallbackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            try {
                if (result.resultCode != Activity.RESULT_OK) {
                    message = "Google sign-in was cancelled."
                    messageError = true
                } else {
                    val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                        .getResult(ApiException::class.java)
                    val token = account.idToken
                        ?: error("Google didn't return an ID token. Check the Web OAuth client configuration.")
                    supabase.auth.signInWith(IDToken) {
                        idToken = token
                        provider = Google
                    }
                    refreshAccount()
                    message = "You're signed in with Google."
                    messageError = false
                }
            } catch (error: Exception) {
                message = error.message?.take(220)
                    ?: "Google sign-in failed (${error::class.simpleName}). Check Google Play services and OAuth configuration."
                messageError = true
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(authSessionState) {
        try { refreshAccount() }
        catch (_: Exception) {
            message = "Couldn't restore your saved session. Check your connection and try again."
            messageError = true
        } finally { loading = false }
    }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try { action() }
            catch (error: Exception) {
                message = error.message?.take(220) ?: "Something went wrong. Please try again."
                messageError = true
            } finally { busy = false }
        }
    }

    suspend fun checkAccountExistsForRecovery(requestedEmail: String): Boolean {
        return withContext(Dispatchers.IO) {
            val connection = (URL("${SoundprintSupabase.SUPABASE_URL}/rest/v1/rpc/soundprint_account_exists_for_recovery").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("apikey", SoundprintSupabase.PUBLISHABLE_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val payload = buildJsonObject { put("p_email", requestedEmail.trim().lowercase()) }.toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    error("We couldn't check your account right now. Please try again. ($status)")
                }
                body.trim().toBooleanStrictOrNull()
                    ?: error("We couldn't verify your account status. Please try again.")
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
                    error("The account request failed ($status). ${body.take(160)}")
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
            deletionCode = ""
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
        perform {
            val payload = buildJsonObject {
                put("action", "delete")
                put("confirmation", "SOUNDPRINT/DELETE")
            }.toString()
            callDeleteAccountFunction(payload)
            supabase.auth.signOut()
            refreshAccount()
            deletionStep = "idle"
            deletionEmail = ""
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
            var handedOffToFallback = false
            try {
                val rawNonce = UUID.randomUUID().toString()
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawNonce.toByteArray(Charsets.UTF_8))
                val hashedNonce = digest.joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }

                val googleOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(SoundprintSupabase.GOOGLE_WEB_CLIENT_ID)
                    .setNonce(hashedNonce)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
                val activity = context.findActivity()
                    ?: error("Google sign-in needs an active Android screen. Close and reopen SoundPrint, then try again.")
                val credentialManager = CredentialManager.create(activity)
                val result = credentialManager.getCredential(context = activity, request = request)
                val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

                supabase.auth.signInWith(IDToken) {
                    idToken = googleCredential.idToken
                    provider = Google
                    nonce = rawNonce
                }
                refreshAccount()
                message = "You're signed in with Google."
                messageError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: NoCredentialException) {
                // Credential Manager found no eligible credential. Open the account
                // picker fallback so users can choose from Google accounts on-device.
                val activity = context.findActivity()
                    ?: error("Google sign-in needs an active Android screen.")
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(SoundprintSupabase.GOOGLE_WEB_CLIENT_ID)
                    .build()
                val client = GoogleSignIn.getClient(activity, options)
                handedOffToFallback = true
                message = "Opening Google sign-in…"
                messageError = false
                googleFallbackLauncher.launch(client.signInIntent)
            } catch (error: GetCredentialProviderConfigurationException) {
                val activity = context.findActivity()
                    ?: error("Google sign-in needs an active Android screen.")
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(SoundprintSupabase.GOOGLE_WEB_CLIENT_ID)
                    .build()
                val client = GoogleSignIn.getClient(activity, options)
                handedOffToFallback = true
                message = "Opening Google sign-in…"
                messageError = false
                googleFallbackLauncher.launch(client.signInIntent)
            } catch (error: GetCredentialException) {
                message = when (error) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException ->
                        "Google sign-in was cancelled."
                    else -> error.message?.take(220)
                        ?: "Google sign-in couldn't start (${error::class.simpleName}). Check Google Play services and try again."
                }
                messageError = true
            } catch (error: Exception) {
                message = error.message?.take(220)
                    ?: "Google sign-in failed (${error::class.simpleName}). Check your connection and OAuth configuration."
                messageError = true
            } finally {
                if (!handedOffToFallback) busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { SoundprintIcon(SoundprintIcons.ArrowLeft, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Account", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Your SoundPrint identity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (signedIn) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                SoundprintIcon(SoundprintIcons.UserRound, null, Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(0.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (loading) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Checking your account…", Modifier.padding(top = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (signedIn && username.isBlank()) {
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
                        singleLine = true
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
                    onClick = { perform { supabase.auth.signOut(); refreshAccount(); message = "Signed out."; messageError = false } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sign out") }
            } else if (signedIn) {
                AccountPanel {
                    Text("YOU'RE SIGNED IN", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("@$username", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(accountEmail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your account is connected. Your music files and playback library remain on this device unless you choose to sync supported data.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AccountPanel {
                    Text("PROFILE", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        leadingIcon = { SoundprintIcon(SoundprintIcons.UserRound, null) },
                        supportingText = { Text("3–20 characters · letters, numbers and underscores") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val clean = nameDraft.trim()
                            val id = userId
                            if (!clean.matches(Regex("[a-z0-9_]{3,20}")) || id == null) {
                                message = "Choose a username with 3–20 letters, numbers or underscores."
                                messageError = true
                            } else perform {
                                supabase.auth.updateUser { data { put("username", clean); put("display_name", clean) } }
                                supabase.from("account_profiles").update(SoundprintProfileUpdate(clean)) {
                                    filter { eq("id", id) }
                                }
                                username = clean
                                displayName = clean
                                message = "Username updated."
                                messageError = false
                            }
                        },
                        enabled = !busy && nameDraft.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save profile") }
                }
                AccountPanel {
                    Text("SECURITY", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(newEmail, { newEmail = it.trim() }, Modifier.fillMaxWidth(), label = { Text("New email address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                    Button(onClick = {
                        if (!newEmail.contains("@")) {
                            message = "Enter a valid email address."
                            messageError = true
                        } else perform {
                            supabase.auth.updateUser { email = newEmail }
                            newEmail = ""
                            message = "Confirmation sent. Check your inboxes to finish changing your email."
                            messageError = false
                        }
                    }, enabled = !busy && newEmail.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Change email") }
                    Spacer(Modifier.height(0.dp))
                    OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(), label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(onClick = {
                        if (newPassword.length < 8) {
                            message = "Use at least 8 characters for your new password."
                            messageError = true
                        } else perform {
                            supabase.auth.updateUser { password = newPassword }
                            newPassword = ""
                            message = "Password updated."
                            messageError = false
                        }
                    }, enabled = !busy && newPassword.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Update password") }
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
                        Text(
                            when (deletionStep) {
                                "email" -> "Enter the email address linked to this SoundPrint account. We'll send a six-digit security code before continuing."
                                "code" -> "Enter the six-digit code sent to $deletionEmail. You can paste the full code into the boxes."
                                else -> "Your email is verified. To permanently delete this account, enter SOUNDPRINT/DELETE below. This action cannot be undone."
                            },
                            fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (deletionStep == "email") {
                            OutlinedTextField(
                                value = deletionEmail,
                                onValueChange = { deletionEmail = it.trim() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Account email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true
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
                                label = { Text("Type SOUNDPRINT/DELETE") },
                                placeholder = { Text("SOUNDPRINT/DELETE") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                singleLine = true
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
                TextButton(onClick = { perform { supabase.auth.signOut(); refreshAccount(); message = "Signed out."; messageError = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(),
                        label = { Text("Confirm new password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
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
                        "forgot" -> "We'll send you a secure link to reset your password."
                        else -> "Sign in to manage your account. Your music library stays on this device by default."
                    },
                    icon = if (mode == "forgot") SoundprintIcons.Lock else SoundprintIcons.AudioWaveform
                )
                if (mode != "forgot") {
                    OutlinedButton(
                        onClick = { signInWithGoogle() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("G", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF4285F4))
                        Spacer(Modifier.width(12.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.height(0.dp))
                        Text("OR", Modifier.padding(horizontal = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(0.dp))
                    }
                }
                OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Email address") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Mail, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                if (mode == "sign-up" || (mode == "sign-in" && !otpRequested)) OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, leadingIcon = { SoundprintIcon(SoundprintIcons.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (mode == "sign-up") OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
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
                                message = "Email verified. You're signed in."
                                password = ""
                                confirmPassword = ""
                            }
                            messageError = false
                        }
                    }, enabled = !busy && otpCode.length == 6, modifier = Modifier.fillMaxWidth()) {
                        Text(if (otpIsRecovery) "Verify reset code" else "Verify email")
                    }
                    TextButton(onClick = {
                        perform {
                            when {
                                otpIsRecovery -> supabase.auth.resetPasswordForEmail(email.trim())
                                otpIsSignup -> supabase.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
                                else -> supabase.auth.signInWith(OTP) { this.email = email.trim() }
                            }
                            otpCode = ""
                            message = "A new verification code has been sent."
                            messageError = false
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (otpIsRecovery) "Resend reset code" else "Resend code")
                    }
                }
                if (!otpRequested) Button(
                    onClick = {
                        when (mode) {
                            "forgot" -> if (!email.contains("@")) {
                                message = "Enter the email address on your account."
                                messageError = true
                            } else perform {
                                val requestedEmail = email.trim().lowercase()
                                if (!checkAccountExistsForRecovery(requestedEmail)) {
                                    otpIsRecovery = false
                                    otpIsSignup = false
                                    otpRequested = false
                                    otpCode = ""
                                    message = "Account not found in database. Please sign up."
                                    messageError = true
                                } else {
                                    supabase.auth.resetPasswordForEmail(
                                        email = requestedEmail,
                                        redirectUrl = SoundprintSupabase.PASSWORD_RESET_REDIRECT
                                    )
                                    otpIsRecovery = false
                                    otpIsSignup = false
                                    otpRequested = false
                                    otpCode = ""
                                    message = "If an account exists for that address, a secure password reset link has been sent to your inbox."
                                    messageError = false
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
                    else Text(when (mode) { "sign-up" -> "Create account"; "forgot" -> "Send reset link"; else -> "Sign in" })
                }
                if (mode == "sign-in" && !otpRequested) TextButton(
                    onClick = {
                        if (!email.contains("@")) {
                            message = "Enter your email address."
                            messageError = true
                        } else perform {
                            supabase.auth.signInWith(OTP) { this.email = email.trim() }
                            otpIsSignup = false
                            otpIsRecovery = false
                            otpRequested = true
                            otpCode = ""
                            message = "Sign-in code sent. Enter it below to sign in."
                            messageError = false
                        }
                    },
                    enabled = !busy && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Email me a sign-in code") }
                if (mode != "forgot" && !otpRequested) TextButton(onClick = { mode = "forgot"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
                if (!otpRequested) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mode == "sign-up") "Already have an account?" else "New to SoundPrint?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { mode = if (mode == "sign-up") "sign-in" else "sign-up"; message = null }) { Text(if (mode == "sign-up") "Sign in" else "Create account") }
                }
                if (otpRequested) TextButton(onClick = { otpRequested = false; otpCode = ""; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                if (mode == "forgot") TextButton(onClick = { mode = "sign-in"; otpIsRecovery = false; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
                }
            }
            if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Working securely…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccountPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .12f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .58f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { content(this) }
}

@Composable
private fun AccountIntroPanel(title: String, description: String, icon: Int) {
    AccountPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .13f),
                modifier = Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f), RoundedCornerShape(16.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SoundprintIcon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("SOUNDPRINT ACCOUNT", fontSize = 10.sp, letterSpacing = 1.45.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(title, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                            modifier = Modifier.weight(1f).height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                ,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(code.getOrNull(index)?.toString().orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                        }
                    }
                }
                Box(Modifier.matchParentSize()) { innerTextField() }
            }
        }
    )
}
