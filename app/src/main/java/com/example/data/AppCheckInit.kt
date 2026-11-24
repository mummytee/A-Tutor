package com.example.data

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

data class AppCheckDiagnosticInfo(
    val providerMode: String = "Not initialized",
    val siteKeyPresent: Boolean = false,
    val debugExtraPresent: Boolean = false,
    val initError: String? = null,
    val tokenCheckSummary: String = "Not checked yet",
    val tokenValid: Boolean? = null
)

object AppCheckDiagnostics {
    private val _state = MutableStateFlow(AppCheckDiagnosticInfo())
    val state: StateFlow<AppCheckDiagnosticInfo> = _state.asStateFlow()

    internal fun update(transform: (AppCheckDiagnosticInfo) -> AppCheckDiagnosticInfo) {
        _state.value = transform(_state.value)
    }

    suspend fun verifyToken(forceRefresh: Boolean = false): AppCheckDiagnosticInfo {
        val result = suspendCancellableCoroutine<Pair<Boolean, String>> { cont ->
            try {
                FirebaseAppCheck.getInstance()
                    .getAppCheckToken(forceRefresh)
                    .addOnSuccessListener { tokenResult ->
                        val token = tokenResult.token
                        val placeholderError = extractPlaceholderError(token)
                        if (placeholderError != null) {
                            Log.e(TAG, "App Check provider returned placeholder error token: $placeholderError")
                            cont.resume(
                                false to "Attestation failed on device ($placeholderError). Verify network/Google Play Services or re-provision App Check."
                            )
                        } else if (token.isBlank()) {
                            cont.resume(false to "App Check returned an empty token.")
                        } else {
                            cont.resume(true to "Valid App Check token obtained.")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "getAppCheckToken failed", e)
                        cont.resume(false to (e.localizedMessage ?: "Failed to obtain App Check token."))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "getAppCheckToken threw synchronously", e)
                cont.resume(false to (e.localizedMessage ?: "App Check is not initialized."))
            }
        }

        val updated = _state.value.copy(
            tokenValid = result.first,
            tokenCheckSummary = result.second
        )
        _state.value = updated
        return updated
    }

    private fun extractPlaceholderError(token: String): String? {
        if (token.isBlank()) return "EMPTY_TOKEN"
        // Firebase App Check encodes {"error":"..."} as single-segment base64 when attestation fails.
        if (!token.contains('.')) {
            return runCatching {
                val decoded = String(Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP))
                val obj = JSONObject(decoded)
                obj.optString("error").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
        return null
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (BuildConfig.DEBUG && !debugToken.isNullOrEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // Synchronous commit() ensures debug token is written before DebugAppCheckProviderFactory reads it
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            Log.d(TAG, "App Check debug secret configured successfully.")

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            AppCheckDiagnostics.update {
                it.copy(
                    providerMode = "Debug Provider (Launch Extra)",
                    debugExtraPresent = true,
                    initError = null
                )
            }
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    AppCheckDiagnostics.update {
                        it.copy(
                            providerMode = "reCAPTCHA Enterprise",
                            siteKeyPresent = true,
                            debugExtraPresent = false,
                            initError = null
                        )
                    }
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    AppCheckDiagnostics.update {
                        it.copy(
                            providerMode = "Missing Site Key",
                            siteKeyPresent = false,
                            initError = "recaptcha_site_key resource string is blank."
                        )
                    }
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                AppCheckDiagnostics.update {
                    it.copy(
                        providerMode = "Missing Site Key",
                        siteKeyPresent = false,
                        initError = "recaptcha_site_key string resource not found in package."
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckDiagnostics.update {
            it.copy(
                providerMode = "Initialization Error",
                initError = e.localizedMessage ?: "Failed to initialize Firebase App Check."
            )
        }
    }
}

