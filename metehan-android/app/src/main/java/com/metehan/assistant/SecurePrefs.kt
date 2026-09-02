package com.metehan.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePrefs {
    private const val PREFS = "metehan_secure_v1"
    private const val KEY_ALIAS = "metehan_openai_key_v1"
    private const val KEY_IV = "api_iv"
    private const val KEY_DATA = "api_data"
    private const val KEY_MODEL = "model"
    const val DEFAULT_MODEL = "gpt-5.6-terra"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun saveApiKey(context: Context, value: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val clean = value.trim()
        if (clean.isBlank()) {
            prefs.edit().remove(KEY_IV).remove(KEY_DATA).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun apiKey(context: Context): String {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val iv = Base64.decode(prefs.getString(KEY_IV, "") ?: "", Base64.NO_WRAP)
            val encrypted = Base64.decode(prefs.getString(KEY_DATA, "") ?: "", Base64.NO_WRAP)
            if (iv.isEmpty() || encrypted.isEmpty()) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun hasApiKey(context: Context): Boolean = apiKey(context).isNotBlank()

    fun saveModel(context: Context, model: String) {
        val clean = model.trim().ifBlank { DEFAULT_MODEL }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODEL, clean).apply()
    }

    fun model(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODEL, DEFAULT_MODEL)
            ?.trim().orEmpty().ifBlank { DEFAULT_MODEL }
}
