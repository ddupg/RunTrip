package com.ddupg.runtrip.data.source

import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedSourceStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `credentials remain encrypted at rest and survive store recreation`() {
        val directory = folder.newFolder()
        val store = EncryptedSourceStore(directory, SourceCipher { key })
        assertNull(store.read(DataSourceId.COROS))
        store.write(DataSourceId.COROS, JSONObject().put("token", "synthetic-private-token"))
        val file = File(directory, "coros.bin")
        assertFalse(file.readText().contains("synthetic-private-token"))
        assertEquals("synthetic-private-token", EncryptedSourceStore(directory, SourceCipher { key }).read(DataSourceId.COROS)!!.getString("token"))
        store.write(DataSourceId.COROS, JSONObject().put("token", "rotated-synthetic-token"))
        assertEquals("rotated-synthetic-token", store.read(DataSourceId.COROS)!!.getString("token"))
        store.clear(DataSourceId.COROS)
        assertNull(store.read(DataSourceId.COROS))
    }

    @Test
    fun `tampered credentials are rejected without silently replacing them`() {
        val directory = folder.newFolder()
        val store = EncryptedSourceStore(directory, SourceCipher { key })
        store.write(DataSourceId.COROS, JSONObject().put("token", "synthetic-private-token"))
        val file = File(directory, "coros.bin")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        try { store.read(DataSourceId.COROS); fail("Authentication tag must be verified") } catch (error: SourceStoreReadException) { assertTrue(error.cause is AEADBadTagException) }
        assertArrayEquals(bytes, file.readBytes())
    }
}
