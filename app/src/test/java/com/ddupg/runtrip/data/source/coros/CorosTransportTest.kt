package com.ddupg.runtrip.data.source.coros

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class CorosTransportTest {
    @Test fun `cancelling a stalled response releases the caller`() = runBlocking {
        ServerSocket(0).use { server ->
            val accepted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val worker = Thread {
                server.accept().use { socket ->
                    socket.getInputStream().bufferedReader().let { reader ->
                        while (!reader.readLine().isNullOrEmpty()) { }
                    }
                    accepted.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }.apply { isDaemon = true; start() }
            try {
                val request = launch(Dispatchers.IO) {
                    UrlConnectionCorosTransport().request("GET", "http://127.0.0.1:${server.localPort}/", emptyMap(), null)
                }
                assertTrue(withContext(Dispatchers.IO) { accepted.await(5, TimeUnit.SECONDS) })
                withTimeout(2_000) { request.cancelAndJoin() }
            } finally {
                release.countDown()
                worker.join(5_000)
            }
        }
    }
}
