package com.closetai.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("closetai_secure", Context.MODE_PRIVATE)
    private val alias = "closetai_session_aes"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun save(session: Session) {
        val raw = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("user", session.userId)
            .put("email", session.email)
            .put("expires", session.expiresAtEpochSeconds)
            .toString()
            .toByteArray()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(raw)

        prefs.edit()
            .putString("blob", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): Session? = runCatching {
        val blob = prefs.getString("blob", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val raw = cipher.doFinal(Base64.decode(blob, Base64.NO_WRAP)).decodeToString()
        val j = JSONObject(raw)

        Session(
            accessToken = j.getString("access"),
            refreshToken = j.getString("refresh"),
            userId = j.getString("user"),
            email = j.optString("email").takeIf { it.isNotBlank() && it != "null" },
            expiresAtEpochSeconds = j.optLong("expires")
        )
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
