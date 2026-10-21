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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ritense.valtimoplugins.smtpmail.BaseTest
import com.ritense.valtimoplugins.smtpmail.dto.Email
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContentDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContextDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailPluginPropertyDto
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory

/**
 * Sends a mail to a throwaway SMTP server to prove that the JavaMail protocol trace is written
 * through the logger. If the trace were still going to stdout the appender below would stay
 * empty, which is what makes this a regression test rather than a smoke test.
 */
class SmtpMailDebugTraceTest : BaseTest() {
    @Test
    fun `writes the SMTP dialogue to the logger when debug is enabled`() {
        val events = sendMailAgainstFakeServer(debug = true, level = Level.DEBUG)
        val trace = events.joinToString("\n")

        assertTrue(trace.contains("MAIL FROM:<afzender@example.com>"), "envelope sender should be traced")
        assertTrue(trace.contains("RCPT TO:<ontvanger@example.com>"), "recipient should be traced")
        assertTrue(trace.contains("RCPT TO:<geheim@example.com>"), "bcc recipient should be traced")
        assertTrue(trace.contains("Subject: Uw aanvraag"), "headers should be traced")
    }

    @Test
    fun `writes nothing at all when debug is disabled`() {
        val events = sendMailAgainstFakeServer(debug = false, level = Level.DEBUG)

        assertTrue(events.isEmpty(), "no trace expected, got: $events")
    }

    @Test
    fun `honours the log level so the trace can be suppressed by configuration`() {
        val events = sendMailAgainstFakeServer(debug = true, level = Level.INFO)

        assertTrue(events.any { it.contains("SMTP debug tracing is enabled") }, "the warning should be logged")
        assertFalse(events.any { it.contains("RCPT TO") }, "the trace itself should be suppressed at INFO")
    }

    private fun sendMailAgainstFakeServer(
        debug: Boolean,
        level: Level,
    ): List<String> {
        val logger = LoggerFactory.getLogger(SmtpMailClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val originalLevel = logger.level

        FakeSmtpServer().use { server ->
            try {
                logger.addAppender(appender)
                logger.level = level

                SmtpMailClient(storageService = mock()).sendEmail(
                    connection = connection(port = server.port, debug = debug),
                    mailContext = mailContext(),
                    mailContent = SmtpMailContentDto(mailMessage = "<p>Hallo</p>", attachments = emptyList()),
                )
            } finally {
                logger.detachAppender(appender)
                logger.level = originalLevel
            }
        }

        return appender.list.map { it.formattedMessage }
    }

    private fun connection(
        port: Int,
        debug: Boolean,
    ) = SmtpMailPluginPropertyDto(
        host = "localhost",
        port = port,
        username = null,
        password = null,
        protocol = "smtp",
        debug = debug,
        auth = false,
        startTlsEnable = false,
    )

    private fun mailContext() =
        SmtpMailContextDto(
            sender = Email("afzender@example.com"),
            fromName = "Gemeente",
            recipients = listOf(Email("ontvanger@example.com")),
            ccList = emptyList(),
            bccList = listOf(Email("geheim@example.com")),
            subject = "Uw aanvraag",
            contentResourceId = "content-resource-id",
            attachmentResourceIds = emptyList(),
        )
}
