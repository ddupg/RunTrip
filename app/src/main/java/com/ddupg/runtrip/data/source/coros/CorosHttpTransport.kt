package com.ddupg.runtrip.data.source.coros

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class CorosHttpResponse(val status: Int, val body: String, val location: String? = null)

internal fun interface CorosHttpTransport {
    suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): CorosHttpResponse
}

internal class UrlConnectionCorosTransport(private val totalTimeoutMillis: Long = 30_000) : CorosHttpTransport {
    companion object {
        private val deadlines = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "coros-deadlines").apply { isDaemon = true }
        }
    }

    override suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): CorosHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val finished = AtomicBoolean(false)
            fun disconnect() { Thread { connection.disconnect() }.apply { isDaemon = true; start() } }
            val deadline = deadlines.schedule({
                if (finished.compareAndSet(false, true)) {
                    continuation.resumeWithException(SocketTimeoutException("COROS request deadline exceeded"))
                    disconnect()
                }
            }, totalTimeoutMillis, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) disconnect()
                deadline.cancel(false)
            }
            Thread {
                try {
                    if (finished.get()) return@Thread
                    connection.requestMethod = method
                    connection.instanceFollowRedirects = false
                    headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                    if (body != null) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    val stream = if (status < 400) connection.inputStream else connection.errorStream
                    val content = ByteArrayOutputStream()
                    stream?.use {
                        val buffer = ByteArray(8192)
                        while (!finished.get()) {
                            val count = it.read(buffer)
                            if (count < 0) break
                            content.write(buffer, 0, count)
                        }
                    }
                    val response = CorosHttpResponse(status, content.toString(Charsets.UTF_8.name()), connection.getHeaderField("Location"))
                    if (finished.compareAndSet(false, true)) continuation.resume(response)
                } catch (error: Exception) {
                    if (finished.compareAndSet(false, true)) continuation.resumeWithException(error)
                } finally {
                    deadline.cancel(false)
                    connection.disconnect()
                }
            }.apply { isDaemon = true; start() }
        }
}
