package com.luoh.music.lrc.data.translation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-encrypted key in noBackupFilesDir: never copied to WebView or Android backup. */
class SecretStorage(private val context: Context, profileId: String = "custom") {
    private val safeProfile = profileId.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "_")
    private val file get() = File(context.noBackupFilesDir, "translation-key-$safeProfile")
    private val alias get() = "translation-key-$safeProfile"
    private val legacyFile get() = File(context.noBackupFilesDir, "translation-key")
    private fun key(aliasName: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(aliasName, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(aliasName, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(value: String) {
        if (value.isBlank()) {
            file.delete()
            if (safeProfile == "custom") legacyFile.delete()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(alias)) }
        file.writeText(Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
    }
    private fun readFile(source: File, aliasName: String): String = runCatching {
        val data = Base64.decode(source.readText(), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(aliasName), GCMParameterSpec(128, data.copyOfRange(0,12)))
        }
        String(cipher.doFinal(data.copyOfRange(12,data.size)), Charsets.UTF_8)
    }.getOrDefault("")
    fun read(): String {
        readFile(file, alias).takeIf { it.isNotBlank() }?.let { return it }
        if (safeProfile == "custom") {
            val legacy = readFile(legacyFile, "translation-key")
            if (legacy.isNotBlank()) {
                save(legacy)
                return legacy
            }
        }
        return ""
    }
}
