package com.notificationsaver.app.data.crypto

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import java.nio.charset.StandardCharsets

class SealedBoxCrypto {
    private val sodium = LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)

    fun generateKeyPair(): KeyPairStrings {
        val pair = sodium.cryptoBoxKeypair()
        return KeyPairStrings(
            encodeKey = encode(pair.publicKey.asBytes),
            decodeKey = encode(pair.secretKey.asBytes),
        )
    }

    fun parseKeyPair(encodeKey: String, decodeKey: String): KeyPairStrings {
        val publicKey = decode32(encodeKey, "encode")
        val secretKey = decode32(decodeKey, "decode")
        val derived = ByteArray(Box.PUBLICKEYBYTES)
        val ok = sodium.cryptoScalarMultBase(derived, secretKey)
        require(ok && derived.contentEquals(publicKey)) {
            "encode and decode keys do not match"
        }
        return KeyPairStrings(
            encodeKey = encode(publicKey),
            decodeKey = encode(secretKey),
        )
    }

    fun seal(plaintext: String, encodeKey: String): String {
        val publicKey = decode32(encodeKey, "encode")
        val message = plaintext.toByteArray(StandardCharsets.UTF_8)
        val cipher = ByteArray(message.size + Box.SEALBYTES)
        val ok = sodium.cryptoBoxSeal(cipher, message, message.size.toLong(), publicKey)
        check(ok) { "seal failed" }
        return encode(cipher)
    }

    companion object {
        private const val FLAGS = Base64.NO_WRAP or Base64.URL_SAFE

        fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, FLAGS)

        fun decode(value: String): ByteArray = Base64.decode(value.trim(), FLAGS)

        private fun decode32(value: String, label: String): ByteArray {
            val bytes = runCatching { decode(value) }.getOrElse {
                throw IllegalArgumentException("$label key is not valid Base64")
            }
            require(bytes.size == Box.PUBLICKEYBYTES) { "$label key must be 32 bytes" }
            return bytes
        }
    }
}

data class KeyPairStrings(
    val encodeKey: String,
    val decodeKey: String,
)
