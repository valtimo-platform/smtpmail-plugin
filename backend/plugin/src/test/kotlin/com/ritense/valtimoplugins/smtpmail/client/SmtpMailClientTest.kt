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
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.smtpmail.BaseTest
import com.ritense.valtimoplugins.smtpmail.dto.Email
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContentDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContextDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailPluginPropertyDto
import jakarta.mail.Message
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import org.springframework.mail.MailSendException
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.Properties

class SmtpMailClientTest : BaseTest() {
    private val client = SmtpMailClient(storageService = mock())

    @Test
    fun `does not enable the JavaMail protocol trace when debug is off`() {
        val sender = client.javaMailSender(connection(debug = false))

        assertFalse(sender.session.debug)
    }

    @Test
    fun `enables the JavaMail protocol trace when debug is on`() {
        val sender = client.javaMailSender(connection(debug = true))

        // Set on the Session, not through the `mail.debug` property: Session reads that one
        // with Properties.getProperty, which ignores the boxed Boolean the plugin holds.
        assertTrue(sender.session.debug)
    }

    @Test
    fun `never writes the protocol trace to stdout`() {
        listOf(false, true).forEach { debug ->
            val sender = client.javaMailSender(connection(debug = debug))

            assertNotSame(System.out, sender.session.debugOut)
        }
    }

    @Test
    fun `writes the protocol trace to the logger and not to stdout`() {
        val sender = client.javaMailSender(connection(debug = true))
        val stdout = ByteArrayOutputStream()
        val originalOut = System.out
        val logger = LoggerFactory.getLogger(SmtpMailClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val originalLevel = logger.level

        try {
            logger.addAppender(appender)
            logger.level = Level.DEBUG
            // Keep the console appender out of it, so stdout only shows what bypasses the logger.
            logger.isAdditive = false
            System.setOut(PrintStream(stdout, true, StandardCharsets.UTF_8))
            sender.session.debugOut.println("DEBUG SMTP: RCPT TO:<geheim@example.com>")
            sender.session.debugOut.flush()
        } finally {
            System.setOut(originalOut)
            logger.detachAppender(appender)
            logger.level = originalLevel
            logger.isAdditive = true
        }

        assertEquals("", stdout.toString(StandardCharsets.UTF_8))
        assertEquals(listOf("DEBUG SMTP: RCPT TO:<geheim@example.com>"), appender.list.map { it.formattedMessage })
    }

    @Test
    fun `keeps the AUTH exchange out of the protocol trace`() {
        val sender = client.javaMailSender(connection(debug = true))

        assertEquals("false", sender.javaMailProperties["mail.debug.auth"])
        assertEquals("false", sender.javaMailProperties["mail.debug.auth.username"])
    }

    @Test
    fun `applies the supplied connection properties`() {
        val sender = client.javaMailSender(connection(debug = false))

        assertEquals("smtp.example.com", sender.host)
        assertEquals(587, sender.port)
        assertEquals("mail-user", sender.username)
        assertEquals("mail-secret", sender.password)
    }

    @Test
    fun `requires STARTTLS on a submission port instead of opening an implicit TLS connection`() {
        val properties = client.javaMailSender(connection(debug = false, port = 587)).javaMailProperties

        assertEquals("true", properties["mail.smtp.starttls.enable"])
        assertEquals("true", properties["mail.smtp.starttls.required"])
        assertEquals("true", properties["mail.smtp.ssl.checkserveridentity"])
        assertNull(properties["mail.smtp.ssl.enable"])
        assertNull(properties["mail.smtp.socketFactory.class"])
    }

    @Test
    fun `opens an implicit TLS connection on port 465`() {
        val properties = client.javaMailSender(connection(debug = false, port = 465)).javaMailProperties

        assertEquals("true", properties["mail.smtp.ssl.enable"])
        assertEquals("true", properties["mail.smtp.ssl.checkserveridentity"])
        assertNull(properties["mail.smtp.starttls.enable"])
    }

    @Test
    fun `rejects authentication without a username or password`() {
        listOf(
            connection(debug = false).copy(username = null),
            connection(debug = false).copy(password = " "),
        ).forEach { connection ->
            assertThrows<IllegalArgumentException> { client.javaMailSender(connection) }
        }
    }

    @Test
    fun `does not hand over credentials when authentication is off`() {
        val sender = client.javaMailSender(connection(debug = false, auth = false))

        assertNull(sender.username)
        assertNull(sender.password)
    }

    @Test
    fun `sets the properties of the configured protocol`() {
        val properties =
            client
                .javaMailSender(
                    connection(debug = false, port = 465).copy(protocol = "smtps"),
                ).javaMailProperties

        assertEquals("true", properties["mail.smtps.auth"])
        assertEquals("true", properties["mail.smtps.ssl.checkserveridentity"])
        assertEquals("600000", properties["mail.smtps.timeout"])
        assertTrue(properties.stringPropertyNames().none { it.startsWith("mail.smtp.") }, "$properties")
    }

    @Test
    fun `accepts the protocol in any case and rejects protocols other than smtp and smtps`() {
        assertEquals("smtps", client.javaMailSender(connection(debug = false).copy(protocol = " SMTPS ")).protocol)
        assertThrows<IllegalArgumentException> {
            client.javaMailSender(
                connection(debug = false).copy(protocol = "imap"),
            )
        }
    }

    @Test
    fun `refuses to send unencrypted when the server does not offer STARTTLS`() {
        lateinit var exception: MailSendException
        val session =
            FakeSmtpServer().use { server ->
                exception =
                    assertThrows<MailSendException> {
                        SmtpMailClient(mock()).sendEmail(
                            connection =
                                connection(debug = false, port = server.port, auth = false)
                                    .copy(host = "localhost", username = null, password = null),
                            mailContext = mailContext(),
                            mailContent = SmtpMailContentDto(mailMessage = "<p>Hallo</p>", attachments = emptyList()),
                        )
                    }
                server.session()
            }

        val causes = generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(causes.any { it.contains("STARTTLS is required") }, "$causes")
        assertEquals("", session.data)
        assertTrue(session.envelopeRecipients.isEmpty(), "${session.envelopeRecipients}")
    }

    @Test
    fun `sets no TLS properties when STARTTLS is off`() {
        val properties = client.javaMailSender(connection(debug = false, startTlsEnable = false)).javaMailProperties

        assertTrue(
            properties.stringPropertyNames().none { it.contains("ssl") || it.contains("starttls") },
            "$properties",
        )
    }

    @Test
    fun `sends Bcc only in the envelope and attaches files with their content type`() {
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        val storage =
            mock<TemporaryResourceStorageService> {
                on { getResourceContentAsInputStream("attachment-id") } doAnswer { pdf.inputStream() }
            }

        val session =
            FakeSmtpServer().use { server ->
                SmtpMailClient(storage).sendEmail(
                    connection =
                        connection(debug = false, port = server.port, startTlsEnable = false, auth = false)
                            .copy(host = "localhost", username = null, password = null),
                    mailContext = mailContext(attachmentResourceIds = listOf("attachment-id")),
                    mailContent =
                        SmtpMailContentDto(
                            mailMessage = "<p>Hallo, één bijlage – € 0</p>",
                            attachments =
                                listOf(
                                    SmtpMailContentDto.Attachment("rapport-één", "attachment-id", "application/pdf"),
                                ),
                        ),
                )
                server.session()
            }
        val message = MimeMessage(Session.getInstance(Properties()), session.data.byteInputStream())

        assertEquals(
            listOf("ontvanger@example.com", "kopie@example.com", "geheim@example.com"),
            session.envelopeRecipients,
        )
        // InternetAddress.equals ignores the personal name, so both parts are compared separately.
        val from = message.from.single() as InternetAddress
        assertEquals("afzender@example.com", from.address)
        assertEquals("Gemeente Súdwest-Fryslân", from.personal)
        assertEquals(
            listOf(InternetAddress("ontvanger@example.com")),
            message.getRecipients(Message.RecipientType.TO).toList(),
        )
        assertEquals(
            listOf(InternetAddress("kopie@example.com")),
            message.getRecipients(Message.RecipientType.CC).toList(),
        )
        assertNull(message.getHeader("Bcc"))
        assertEquals("Uw aanvraag – één bijlage", message.subject)

        val parts = leafParts(message)
        val html = parts.single { it.isMimeType("text/html") }
        assertEquals("<p>Hallo, één bijlage – € 0</p>", html.content)
        val attachment = parts.single { Part.ATTACHMENT.equals(it.disposition, ignoreCase = true) }
        assertEquals("rapport-één", attachment.fileName)
        assertTrue(attachment.isMimeType("application/pdf"), attachment.contentType)
        assertArrayEquals(pdf, attachment.inputStream.readAllBytes())
    }

    private fun mailContext(attachmentResourceIds: List<String> = emptyList()) =
        SmtpMailContextDto(
            sender = Email("afzender@example.com"),
            fromName = "Gemeente Súdwest-Fryslân",
            recipients = listOf(Email("ontvanger@example.com")),
            ccList = listOf(Email("kopie@example.com")),
            bccList = listOf(Email("geheim@example.com")),
            subject = "Uw aanvraag – één bijlage",
            contentResourceId = null,
            attachmentResourceIds = attachmentResourceIds,
            content = "<p>Hallo, één bijlage – € 0</p>",
        )

    private fun leafParts(part: Part): List<Part> =
        when (val content = part.content) {
            is MimeMultipart -> (0 until content.count).flatMap { leafParts(content.getBodyPart(it)) }
            else -> listOf(part)
        }

    private fun connection(
        debug: Boolean,
        port: Int = 587,
        startTlsEnable: Boolean = true,
        auth: Boolean = true,
    ) = SmtpMailPluginPropertyDto(
        host = "smtp.example.com",
        port = port,
        username = "mail-user",
        password = "mail-secret",
        protocol = "smtp",
        debug = debug,
        auth = auth,
        startTlsEnable = startTlsEnable,
    )
}
