package com.example.diary.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM + PBKDF2 加密工具。
 *
 * 加密文本格式：enc1:<base64_salt>:<base64_iv>:<base64_ciphertext+tag>
 * - salt: 16 字节
 * - iv: 12 字节
 * - PBKDF2WithHmacSHA256, 200000 iters, 256-bit key
 * - 算法本身没人能破；安全等级取决于密码强度
 */
object CryptoUtil {
    private const val PREFIX = "enc1:"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 200000
    private const val KEY_LEN_BITS = 256

    fun isEncrypted(s: String?): Boolean {
        return s != null && s.startsWith(PREFIX)
    }

    fun encrypt(plain: String, password: String): String {
        if (password.isEmpty()) return plain
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX +
            Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    /** 失败抛异常（密码错或数据损坏）。调用方应捕获并提示用户。 */
    fun decrypt(blob: String, password: String): String {
        if (!isEncrypted(blob)) return blob
        val parts = blob.removePrefix(PREFIX).split(":")
        require(parts.size == 3) { "格式错误" }
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ct = Base64.decode(parts[2], Base64.NO_WRAP)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val plain = cipher.doFinal(ct)
        return String(plain, Charsets.UTF_8)
    }

    /** 安全解密：失败返回 null，不抛异常。 */
    fun tryDecrypt(blob: String, password: String): String? {
        return try { decrypt(blob, password) } catch (e: Throwable) { null }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
