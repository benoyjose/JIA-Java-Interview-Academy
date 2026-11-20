package com.jia.academy.app.auth

import android.app.Activity
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

@Composable
fun AuthScreen(
    repository: AuthRepository,
    baseUrl: String,
    onAuthenticated: (JiaSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(repository))
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val loading = authState.loading
    val error = authState.error
    val otpRequested = authState.otpRequested
    LaunchedEffect(authState.session) {
        val session = authState.session ?: return@LaunchedEffect
        runCatching {
            val userJson = org.json.JSONObject().put("id", session.account.id)
                .put("name", session.account.name).put("email", session.account.email)
                .put("roles", org.json.JSONArray(session.account.roles)).toString()
            SecureSessionStore(context).save(session.accessToken, session.refreshToken, session.expiresAt, userJson)
        }.onSuccess {
            onAuthenticated(session)
            authViewModel.consumeSession()
        }.onFailure {
            authViewModel.setError("Could not securely save this session.")
            authViewModel.consumeSession()
        }
    }
    var registering by remember { mutableStateOf(false) }
    var phoneMode by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var googleLoading by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (phoneMode) "Sign in with phone" else if (registering) "Create your JIA account" else "Sign in to JIA",
            style = MaterialTheme.typography.headlineSmall)
        Text("You can continue browsing public lessons without signing in.")
        Button(
            enabled = !loading && !googleLoading && com.jia.academy.app.BuildConfig.JIA_GOOGLE_WEB_CLIENT_ID.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val activity = context as? Activity
                if (activity == null) { authViewModel.setError("Google sign-in requires an active Android screen."); return@Button }
                if (com.jia.academy.app.BuildConfig.JIA_GOOGLE_WEB_CLIENT_ID.isBlank()) {
                    authViewModel.setError("Configure JIA_GOOGLE_WEB_CLIENT_ID in your local Gradle properties.")
                    return@Button
                }
                googleLoading = true
                authViewModel.setError(null)
                scope.launch {
                    runCatching {
                        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
                        val nonce = Base64.encodeToString(random, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                        val option = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(com.jia.academy.app.BuildConfig.JIA_GOOGLE_WEB_CLIENT_ID)
                            .setNonce(nonce)
                            .build()
                        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                        val credential = CredentialManager.create(context).getCredential(activity, request).credential
                        if (credential !is CustomCredential ||
                            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            throw IllegalStateException("Google did not return a supported ID token.")
                        }
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        googleCredential.idToken to nonce
                    }.onSuccess { (idToken, nonceValue) ->
                        authViewModel.loginWithGoogle(baseUrl, idToken, nonceValue)
                    }.onFailure { authViewModel.setError(it.message ?: "Google sign-in failed.") }
                    googleLoading = false
                }
            }
        ) { Text("Continue with Google") }
        if (com.jia.academy.app.BuildConfig.JIA_GOOGLE_WEB_CLIENT_ID.isBlank()) {
            Text("Google sign-in needs a configured OAuth Web Client ID.", style = MaterialTheme.typography.bodySmall)
        }
        if (phoneMode) {
            OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it },
                label = { Text("Phone number (E.164, e.g. +919876543210)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (otpRequested) {
                OutlinedTextField(value = otpCode, onValueChange = { otpCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit verification code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                loading = true
                error = null
                scope.launch {
                    runCatching {
                        if (otpRequested) repository.verifyPhoneOtp(baseUrl, phoneNumber.trim(), otpCode)
                        else { repository.requestPhoneOtp(baseUrl, phoneNumber.trim()); null }
                    }.onSuccess { session ->
                        if (session == null) otpRequested = true
                        else {
                            runCatching {
                                val userJson = org.json.JSONObject().put("id", session.account.id)
                                    .put("name", session.account.name).put("email", session.account.email)
                                    .put("roles", org.json.JSONArray(session.account.roles)).toString()
                                SecureSessionStore(context).save(session.accessToken, session.refreshToken, session.expiresAt, userJson)
                            }.onSuccess { onAuthenticated(session) }
                                .onFailure { error = "Could not securely save this session." }
                        }
                    }.onFailure { error = it.message ?: "Phone verification failed." }
                    loading = false
                }
            }, enabled = !loading && phoneNumber.startsWith("+") && phoneNumber.count { it.isDigit() } in 8..15 &&
                (!otpRequested || otpCode.length == 6), modifier = Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator() else Text(if (otpRequested) "Verify and sign in" else "Send verification code")
            }
            if (otpRequested) TextButton(onClick = { otpRequested = false; otpCode = ""; error = null }) { Text("Change phone number") }
            TextButton(onClick = { phoneMode = false; otpRequested = false; error = null }) { Text("Use email instead") }
        } else {
        if (registering) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = password, onValueChange = { password = it },
            label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (registering) {
            Text("Use at least 12 characters for your password.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    runCatching {
                        if (registering) repository.register(baseUrl, name.trim(), email.trim(), password)
                        else repository.login(baseUrl, email.trim(), password)
                    }.onSuccess { session ->
                        runCatching {
                            val userJson = org.json.JSONObject()
                                .put("id", session.account.id)
                                .put("name", session.account.name)
                                .put("email", session.account.email)
                                .put("roles", org.json.JSONArray(session.account.roles))
                                .toString()
                            SecureSessionStore(context).save(session.accessToken, session.refreshToken, session.expiresAt, userJson)
                        }.onSuccess { onAuthenticated(session) }
                            .onFailure { error = "Could not securely save this session." }
                    }.onFailure { error = it.message ?: "Authentication failed." }
                    loading = false
                }
            },
            enabled = !loading && email.isNotBlank() && password.isNotBlank() &&
                (!registering || (name.isNotBlank() && password.length >= 12)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator() else Text(if (registering) "Create account" else "Sign in")
        }
        TextButton(onClick = { registering = !registering; error = null }) {
            Text(if (registering) "Already have an account? Sign in" else "New to JIA? Create an account")
        }
        TextButton(onClick = { phoneMode = true; registering = false; error = null }) {
            Text("Continue with phone number")
        }
        }
    }
}
