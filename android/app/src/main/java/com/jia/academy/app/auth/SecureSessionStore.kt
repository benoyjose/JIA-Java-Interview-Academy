package com.jia.academy.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores encrypted session material; the AES key is non-exportable in Android Keystore. */
class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("jia_secure_session", Context.MODE_PRIVATE)
    private val alias = "jia.session.aes.v1"

    fun save(accessToken: String, expiresAt: String, userJson: String) {
        val plain = """{"accessToken":${org.json.JSONObject.quote(accessToken)},"expiresAt":${org.json.JSONObject.quote(expiresAt)},"user":$userJson}"""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun accessToken(): String? = runCatching {
        val iv = Base64.decode(preferences.getString("iv", null), Base64.NO_WRAP)
        val payload = Base64.decode(preferences.getString("payload", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        org.json.JSONObject(String(cipher.doFinal(payload), Charsets.UTF_8)).getString("accessToken")
    }.getOrNull()

    fun clear() { preferences.edit().clear().apply() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
