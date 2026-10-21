/*
 * Copyright 2015-2022 Ritense BV, the Netherlands.
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

import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContentDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContextDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailPluginPropertyDto
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.springframework.core.io.InputStreamSource
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

@SkipComponentScan
@Component
class SmtpMailClient(
    private val storageService: TemporaryResourceStorageService,
) {
    fun sendEmail(
        connection: SmtpMailPluginPropertyDto,
        mailContext: SmtpMailContextDto,
        mailContent: SmtpMailContentDto,
    ) {
        // Outside the try, so a configuration error is reported as itself.
        val javaMailSender = javaMailSender(connection)
        try {
            val message: MimeMessage = javaMailSender.createMimeMessage()

            with(MimeMessageHelper(message, true, StandardCharsets.UTF_8.name())) {
                setFrom(mailContext.sender.address, mailContext.fromName)
                mailContext.recipients.forEach { addTo(it.address) }
                mailContext.ccList.forEach { addCc(it.address) }
                mailContext.bccList.forEach { addBcc(it.address) }
                setSubject(mailContext.subject)
                setText(mailContent.mailMessage, true)
                mailContent.attachments.forEach { attachment ->
                    val source =
                        InputStreamSource { storageService.getResourceContentAsInputStream(attachment.fileResourceId) }
                    if (attachment.contentType == null) {
                        addAttachment(attachment.fileName, source)
                    } else {
                        addAttachment(attachment.fileName, source, attachment.contentType)
                    }
                }

                javaMailSender.send(message)
            }
        } catch (e: Exception) {
            throw MailSendException("Failed to send mail: ${e.message}", e)
        }
    }

    internal fun javaMailSender(connection: SmtpMailPluginPropertyDto): JavaMailSenderImpl =
        JavaMailSenderImpl().apply {
            with(connection) {
                this@apply.host = host.trim()
                this@apply.port = port
                // JavaMail would only report 'no password specified?' at connect time.
                require(!auth || (!username.isNullOrBlank() && !password.isNullOrBlank())) {
                    "Authentication is on but the username or password is empty"
                }
                // JavaMail logs in whenever it has credentials, so they are only handed over when authentication is on.
                if (auth && !username.isNullOrBlank()) this@apply.username = username
                if (auth && !password.isNullOrBlank()) this@apply.password = password
                val protocol = protocol.trim().lowercase()
                require(protocol in SUPPORTED_PROTOCOLS) { "Unsupported protocol '$protocol', use smtp or smtps" }
                this@apply.protocol = protocol
                this@apply.javaMailProperties["mail.transport.protocol"] = protocol
                // JavaMail only reads the properties of the protocol in use, e.g. mail.smtps.* for smtps.
                val prefix = "mail.$protocol"
                this@apply.javaMailProperties["$prefix.auth"] = auth.toString()
                this@apply.javaMailProperties["$prefix.connectiontimeout"] = CONNECTION_TIMEOUT_MILLIS.toString()
                this@apply.javaMailProperties["$prefix.timeout"] = READ_TIMEOUT_MILLIS.toString()
                this@apply.javaMailProperties["$prefix.writetimeout"] = WRITE_TIMEOUT_MILLIS.toString()
                if (startTlsEnable) {
                    // Port 465 is implicit TLS (RFC 8314); any other port must upgrade with STARTTLS, never fall back to plaintext.
                    if (port == IMPLICIT_TLS_PORT) {
                        this@apply.javaMailProperties["$prefix.ssl.enable"] = "true"
                    } else {
                        this@apply.javaMailProperties["$prefix.starttls.enable"] = "true"
                        this@apply.javaMailProperties["$prefix.starttls.required"] = "true"
                    }
                    this@apply.javaMailProperties["$prefix.ssl.checkserveridentity"] = "true"
                }
                // Keep the AUTH exchange and the account name out of the trace even when debug is on.
                this@apply.javaMailProperties["mail.debug.auth"] = "false"
                this@apply.javaMailProperties["mail.debug.auth.username"] = "false"
            }

            // Build the session ourselves so that the protocol trace is written through the
            // logger instead of stdout, where it would bypass log level, appender and
            // retention configuration entirely.
            //
            // Debug is switched on through Session.setDebug rather than the `mail.debug`
            // property, for two reasons: the property is read with Properties.getProperty and
            // therefore only honours String values, and setting it up front would make the
            // Session constructor itself trace to stdout before we can redirect it.
            session =
                Session.getInstance(javaMailProperties).apply {
                    debugOut = PrintStream(LineLoggingOutputStream(), true, StandardCharsets.UTF_8)
                    setDebug(connection.debug)
                }

            if (connection.debug) {
                logger.warn {
                    "SMTP debug tracing is enabled for host '${connection.host}'. " +
                        "The full SMTP dialogue - envelope recipients including Bcc, headers and message content - " +
                        "is written to logger '$DEBUG_LOGGER_NAME' at DEBUG level."
                }
            }
        }

    /**
     * Collects JavaMail's protocol trace line by line and hands each line to the logger.
     */
    private class LineLoggingOutputStream : OutputStream() {
        private val line = ByteArrayOutputStream(INITIAL_LINE_BUFFER_SIZE)

        override fun write(b: Int) {
            when (b) {
                '\n'.code -> flushLine()
                '\r'.code -> Unit
                else -> line.write(b)
            }
        }

        override fun flush() = flushLine()

        override fun close() = flushLine()

        private fun flushLine() {
            if (line.size() == 0) return
            val message = line.toString(StandardCharsets.UTF_8)
            line.reset()
            logger.debug { message }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private val DEBUG_LOGGER_NAME = SmtpMailClient::class.java.name

        private const val INITIAL_LINE_BUFFER_SIZE = 256

        private const val IMPLICIT_TLS_PORT = 465

        private val SUPPORTED_PROTOCOLS = setOf("smtp", "smtps")

        // RFC 5321 4.5.3.2: a relay may take up to 10 minutes to accept the data, so a shorter wait risks a resend.
        private const val CONNECTION_TIMEOUT_MILLIS = 10_000

        private const val READ_TIMEOUT_MILLIS = 600_000

        private const val WRITE_TIMEOUT_MILLIS = 180_000
    }
}
