package com.ddupg.runtrip.data.source.coros

import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.NonCancellable
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
    @Test fun `total deadline closes a continuously streaming response even in noncancellable context`() = runBlocking {
        ServerSocket(0).use { server ->
            val closed = CountDownLatch(1)
            val stop = AtomicBoolean(false)
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().let { reader ->
                            while (!reader.readLine().isNullOrEmpty()) { }
                        }
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 100000000\r\n\r\n".toByteArray())
                        while (!stop.get()) {
                            output.write(ByteArray(1024))
                            output.flush()
                            Thread.sleep(10)
                        }
                    }
                } catch (_: java.io.IOException) {
                    closed.countDown()
                }
            }.apply { isDaemon = true; start() }
            try {
                try {
                    withTimeout(3_000) {
                        withContext(NonCancellable) {
                            UrlConnectionCorosTransport(300).request("GET", "http://127.0.0.1:${server.localPort}/", emptyMap(), null)
                        }
                    }
                    throw AssertionError("deadline must interrupt streaming response")
                } catch (_: SocketTimeoutException) { }
                assertTrue(withContext(Dispatchers.IO) { closed.await(3, TimeUnit.SECONDS) })
            } finally {
                stop.set(true)
                worker.join(5_000)
            }
        }
    }

    @Test fun `cancelling a streaming response closes the connection`() = runBlocking {
        ServerSocket(0).use { server ->
            val streaming = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val stop = AtomicBoolean(false)
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().let { reader ->
                            while (!reader.readLine().isNullOrEmpty()) { }
                        }
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 100000000\r\n\r\n".toByteArray())
                        while (!stop.get()) {
                            output.write(ByteArray(1024))
                            output.flush()
                            streaming.countDown()
                            Thread.sleep(10)
                        }
                    }
                } catch (_: java.io.IOException) {
                    closed.countDown()
                }
            }.apply { isDaemon = true; start() }
            try {
                val request = launch(Dispatchers.IO) {
                    UrlConnectionCorosTransport().request("GET", "http://127.0.0.1:${server.localPort}/", emptyMap(), null)
                }
                assertTrue(withContext(Dispatchers.IO) { streaming.await(3, TimeUnit.SECONDS) })
                withTimeout(2_000) { request.cancelAndJoin() }
                assertTrue(withContext(Dispatchers.IO) { closed.await(3, TimeUnit.SECONDS) })
            } finally {
                stop.set(true)
                worker.join(5_000)
            }
        }
    }

}
