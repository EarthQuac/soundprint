package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put

@Serializable
private data class SoundprintProfileUpdate(@SerialName("display_name") val displayName: String)

@Composable
internal fun SoundprintAccountScreen(onBack: () -> Unit) {
    val supabase = remember { SoundprintSupabase.client }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    var accountEmail by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var nameDraft by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("sign-in") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }

    suspend fun refreshAccount() {
        val user = supabase.auth.currentUserOrNull()
        if (user == null) {
            signedIn = false
            userId = null
            accountEmail = ""
            displayName = ""
        } else {
            signedIn = true
            userId = user.id
            accountEmail = user.email.orEmpty()
            displayName = user.userMetadata?.get("display_name")?.toString()?.trim('"')
                ?.ifBlank { null } ?: accountEmail.substringBefore("@").ifBlank { "Listener" }
            nameDraft = displayName
        }
    }

    LaunchedEffect(Unit) {
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Account", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Your SoundPrint identity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (signedIn) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Icon(Icons.Filled.Person, null, Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (loading) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Checking your account…", Modifier.padding(top = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (signedIn) {
                AccountPanel {
                    Text("YOU'RE SIGNED IN", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(accountEmail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your account is connected. Your music files and playback library remain on this device unless you choose to sync supported data.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AccountPanel {
                    Text("PROFILE", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(nameDraft, { nameDraft = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
                    Button(
                        onClick = {
                            val clean = nameDraft.trim()
                            val id = userId
                            if (clean.isBlank() || id == null) {
                                message = "Enter a display name first."
                                messageError = true
                            } else perform {
                                supabase.auth.updateUser { data { put("display_name", clean) } }
                                supabase.from("account_profiles").update(SoundprintProfileUpdate(clean)) {
                                    filter { eq("id", id) }
                                }
                                displayName = clean
                                message = "Profile updated."
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
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
                TextButton(onClick = { perform { supabase.auth.signOut(); refreshAccount(); message = "Signed out."; messageError = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            } else {
                AccountPanel {
                    Text("YOUR MUSIC. YOUR ACCOUNT.", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when (mode) { "sign-up" -> "Create your SoundPrint account."; "forgot" -> "Reset your password."; else -> "Welcome back." },
                        fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("Sign in to keep your SoundPrint identity ready across devices. Your music library stays local by default.", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mode == "sign-up") OutlinedTextField(displayName, { displayName = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Display name") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true)
                OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Email address") }, leadingIcon = { Icon(Icons.Filled.MailOutline, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                if (mode != "forgot") OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (mode == "sign-up") OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(
                    onClick = {
                        when (mode) {
                            "forgot" -> if (!email.contains("@")) {
                                message = "Enter the email address on your account."
                                messageError = true
                            } else perform {
                                supabase.auth.resetPasswordForEmail(email.trim(), redirectUrl = SoundprintSupabase.AUTH_REDIRECT)
                                message = "Password reset email sent. Follow the link in your inbox, then return here to sign in."
                                messageError = false
                            }
                            "sign-up" -> if (displayName.isBlank() || !email.contains("@") || password.length < 8 || password != confirmPassword) {
                                message = "Enter a display name, valid email, matching passwords and at least 8 password characters."
                                messageError = true
                            } else perform {
                                supabase.auth.signUpWith(Email, redirectUrl = SoundprintSupabase.AUTH_REDIRECT) {
                                    this.email = email.trim()
                                    this.password = password
                                    data = kotlinx.serialization.json.buildJsonObject { put("display_name", displayName.trim()) }
                                }
                                refreshAccount()
                                message = if (signedIn) "Your SoundPrint account is ready." else "Account created. Check your email and verify your address before signing in."
                                messageError = false
                                password = ""
                                confirmPassword = ""
                            }
                            else -> if (!email.contains("@") || password.isBlank()) {
                                message = "Enter your email and password."
                                messageError = true
                            } else perform {
                                supabase.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
                                refreshAccount()
                                message = "You're signed in."
                                messageError = false
                                password = ""
                            }
                        }
                    },
                    enabled = !busy && email.isNotBlank() && (mode == "forgot" || password.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(when (mode) { "sign-up" -> "Create account"; "forgot" -> "Send reset email"; else -> "Sign in" })
                }
                if (mode != "forgot") TextButton(onClick = { mode = "forgot"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mode == "sign-up") "Already have an account?" else "New to SoundPrint?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { mode = if (mode == "sign-up") "sign-in" else "sign-up"; message = null }) { Text(if (mode == "sign-up") "Sign in" else "Create account") }
                }
                if (mode == "forgot") TextButton(onClick = { mode = "sign-in"; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
            }
            if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Working securely…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background((if (messageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = .10f)).padding(14.dp), fontSize = 12.sp, lineHeight = 18.sp, color = if (messageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun AccountPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f), RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content(this) }
}
