/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.smtpmail.client

import java.io.PrintStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * The bare minimum of RFC 5321 needed to get one message accepted, recording what the client sent.
 */
class FakeSmtpServer : AutoCloseable {
    private val server = ServerSocket(0)
    private val pool = Executors.newSingleThreadExecutor()
    private val session: Future<FakeSmtpSession> = pool.submit<FakeSmtpSession> { serve() }

    val port: Int = server.localPort

    /** Waits for the client to finish its session and returns what it sent. */
    fun session(): FakeSmtpSession = session.get(SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    override fun close() {
        pool.shutdownNow()
        server.close()
    }

    private fun serve(): FakeSmtpSession {
        val recipients = mutableListOf<String>()
        val data = StringBuilder()
        server.accept().use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            val writer = PrintStream(socket.getOutputStream(), true, StandardCharsets.UTF_8)
            writer.print("220 localhost fake ESMTP\r\n")
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("EHLO", true) -> writer.print("250-localhost\r\n250 OK\r\n")
                    line.startsWith("RCPT TO:", true) -> {
                        recipients += line.substringAfter(':').trim().removeSurrounding("<", ">")
                        writer.print("250 OK\r\n")
                    }
                    line.startsWith("DATA", true) -> {
                        writer.print("354 Start mail input\r\n")
                        while (true) {
                            val dataLine = reader.readLine() ?: break
                            if (dataLine == ".") break
                            data.append(dataLine).append('\n')
                        }
                        writer.print("250 OK queued\r\n")
                    }
                    line.startsWith("QUIT", true) -> {
                        writer.print("221 Bye\r\n")
                        break
                    }
                    else -> writer.print("250 OK\r\n")
                }
            }
        }
        return FakeSmtpSession(recipients, data.toString())
    }

    data class FakeSmtpSession(
        val envelopeRecipients: List<String>,
        val data: String,
    )

    companion object {
        private const val SESSION_TIMEOUT_SECONDS = 10L
    }
}
