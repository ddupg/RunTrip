package com.ddupg.runtrip.data.source

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

interface SourceStore {
    fun read(id: DataSourceId): JSONObject?
    fun write(id: DataSourceId, value: JSONObject)
    fun clear(id: DataSourceId)
}

internal class SourceCipher(private val key: () -> SecretKey) {
    fun encrypt(plain: ByteArray, source: DataSourceId): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(source.code.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
    }

    fun decrypt(bytes: ByteArray, source: DataSourceId): ByteArray {
        require(bytes.isNotEmpty())
        val ivSize = bytes[0].toInt() and 0xff
        require(ivSize == 12 && bytes.size > 1 + ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        cipher.updateAAD(source.code.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size))
    }
}

internal class EncryptedSourceStore(
    private val directory: File,
    private val cipher: SourceCipher,
) : SourceStore {
    private fun file(id: DataSourceId) = AtomicFile(File(directory, "${id.code}.bin"))

    override fun read(id: DataSourceId): JSONObject? {
        val file = file(id)
        val bytes = try {
            file.readFully()
        } catch (missing: FileNotFoundException) {
            if (!file.baseFile.exists()) return null
            throw missing
        }
        val envelope = JSONObject(String(cipher.decrypt(bytes, id), Charsets.UTF_8))
        require(envelope.getInt("version") == 1) { "Unsupported data source storage version" }
        return envelope.getJSONObject("value")
    }

    override fun write(id: DataSourceId, value: JSONObject) {
        check(directory.isDirectory || directory.mkdirs())
        val envelope = JSONObject().put("version", 1).put("value", value)
        val bytes = cipher.encrypt(envelope.toString().toByteArray(Charsets.UTF_8), id)
        val file = file(id)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    override fun clear(id: DataSourceId) = file(id).delete()
}

internal fun androidSourceCipher(): SourceCipher = SourceCipher {
    val alias = "runtrip.data-sources.v1"
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existing = keyStore.getKey(alias, null) as SecretKey?
    existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        generateKey()
    }
}
