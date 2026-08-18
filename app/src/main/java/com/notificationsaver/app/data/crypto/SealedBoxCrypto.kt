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

    fun seal(plaintext: String, encodeKey: String): String {
        val publicKey = decode(encodeKey)
        require(publicKey.size == Box.PUBLICKEYBYTES) { "encode key must be 32 bytes" }
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
    }
}

data class KeyPairStrings(
    val encodeKey: String,
    val decodeKey: String,
)
