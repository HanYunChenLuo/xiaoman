package com.xiaoman.app

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 中的 AES-GCM 密钥加密敏感 token 后写入 SharedPreferences。
 *
 * 格式：`enc_<key>` 存 [IV 长度 1B][IV][密文+tag]，Base64。旧明文自动迁移。
 * Keystore 不可用时退回明文键 `<key>`，保证登录流程可用。
 */
object SecretStore {
    private const val KEY_ALIAS = "xiaoman_cloud_token_key"
    private const val PREFIX = "enc_"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 写入加密值；返回 false 表示 Keystore 不可用且尚未写入。 */
    fun put(prefs: SharedPreferences, key: String, value: String): Boolean {
        val editor = prefs.edit()
        if (value.isEmpty()) {
            editor.remove(PREFIX + key).apply()
            return true
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(1 + iv.size + body.size)
            packed[0] = iv.size.toByte()
            System.arraycopy(iv, 0, packed, 1, iv.size)
            System.arraycopy(body, 0, packed, 1 + iv.size, body.size)
            editor.putString(PREFIX + key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 读取加密值；没有加密值时回退读取旧明文键。 */
    fun get(prefs: SharedPreferences, key: String): String {
        val raw = prefs.getString(PREFIX + key, "") ?: ""
        if (raw.isEmpty()) return prefs.getString(key, "") ?: ""
        return try {
            val packed = Base64.decode(raw, Base64.NO_WRAP)
            val ivLength = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivLength)
            val body = packed.copyOfRange(1 + ivLength, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    /** 同时清除加密值与旧明文键。 */
    fun clear(prefs: SharedPreferences, key: String) {
        prefs.edit().remove(PREFIX + key).remove(key).apply()
    }
}
