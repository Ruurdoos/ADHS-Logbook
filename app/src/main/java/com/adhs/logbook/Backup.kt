package com.adhs.logbook

import com.adhs.logbook.shared.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable v1: ASCII ADHSBK01, 16-byte salt, 12-byte nonce, AES-256-GCM ciphertext+tag.
 * UTF-8 JSON payload, PBKDF2-HMAC-SHA256 (600000 rounds); header is authenticated.
 */
object BackupCrypto {
    const val MAX_BYTES=32*1024*1024
    private val magic="ADHSBK01".toByteArray(Charsets.US_ASCII)
    private fun key(passphrase: CharArray,salt: ByteArray): ByteArray {
        require(passphrase.size>=10)
        val spec=PBEKeySpec(passphrase,salt,600000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun encrypt(doc: BackupDocument,passphrase: CharArray): ByteArray {
        val bytes=BackupFormat.encode(doc).toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_BYTES-52)
        val random=SecureRandom();val salt=ByteArray(16).also(random::nextBytes);val nonce=ByteArray(12).also(random::nextBytes)
        val header=magic+salt+nonce;val secret=key(passphrase,salt)
        return try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(secret,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD(header)
            header+cipher.doFinal(bytes)
        } finally { secret.fill(0);bytes.fill(0) }
    }
    fun decrypt(bytes: ByteArray,passphrase: CharArray): BackupDocument {
        require(bytes.size in 52..MAX_BYTES && bytes.copyOfRange(0,8).contentEquals(magic))
        val secret=key(passphrase,bytes.copyOfRange(8,24))
        val plain=try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(secret,"AES"),GCMParameterSpec(128,bytes.copyOfRange(24,36)))
            cipher.updateAAD(bytes.copyOfRange(0,36));cipher.doFinal(bytes.copyOfRange(36,bytes.size))
        } finally { secret.fill(0) }
        return try { BackupFormat.decode(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }
}
