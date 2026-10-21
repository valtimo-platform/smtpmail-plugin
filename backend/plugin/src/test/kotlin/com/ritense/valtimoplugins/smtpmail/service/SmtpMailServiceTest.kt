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

package com.ritense.valtimoplugins.smtpmail.service

import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.smtpmail.BaseTest
import com.ritense.valtimoplugins.smtpmail.client.SmtpMailClient
import com.ritense.valtimoplugins.smtpmail.dto.Email
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContentDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContextDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailPluginPropertyDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SmtpMailServiceTest : BaseTest() {
    private lateinit var smtpMailClient: SmtpMailClient
    private lateinit var storageService: TemporaryResourceStorageService
    private lateinit var service: SmtpMailService

    @BeforeEach
    fun setUp() {
        smtpMailClient = mock()
        storageService = mock()
        service = SmtpMailService(smtpMailClient, storageService)
    }

    @Test
    fun `sends the content from the temporary file`() {
        whenever(storageService.getResourceContentAsInputStream("content-resource-id"))
            .doReturn("<p>Uit bestand</p>".byteInputStream())

        service.sendSmtpMail(mailContext(contentResourceId = "content-resource-id"), connection)

        assertEquals("<p>Uit bestand</p>", captureMailContent().mailMessage)
    }

    @Test
    fun `sends the content directly without reading a temporary file`() {
        service.sendSmtpMail(mailContext(content = "<p>Uit variabele</p>"), connection)

        assertEquals("<p>Uit variabele</p>", captureMailContent().mailMessage)
        verify(storageService, never()).getResourceContentAsInputStream(any())
    }

    @Test
    fun `keeps the attachment file name and passes the MIME type as content type`() {
        givenAttachment("met-naam", mapOf("filename" to "rapport", "contentType" to "application/pdf"))
        givenAttachment("zonder-naam", mapOf("contentType" to "image/png"))
        givenAttachment("zonder-metadata", emptyMap())

        service.sendSmtpMail(
            mailContext(
                content = "<p>Bijlagen</p>",
                attachmentResourceIds = listOf("met-naam", "zonder-naam", "zonder-metadata"),
            ),
            connection,
        )

        assertEquals(
            listOf(
                SmtpMailContentDto.Attachment("rapport", "met-naam", "application/pdf"),
                SmtpMailContentDto.Attachment("attachment", "zonder-naam", "image/png"),
                SmtpMailContentDto.Attachment("attachment", "zonder-metadata", null),
            ),
            captureMailContent().attachments,
        )
    }

    @Test
    fun `drops a content type that would fail the mail and strips control characters and paths from the file name`() {
        givenAttachment("leeg", mapOf("filename" to "a.pdf", "contentType" to ""))
        givenAttachment("geen-mime-type", mapOf("filename" to "b.pdf", "contentType" to "pdf"))
        givenAttachment(
            "met-header",
            mapOf(
                "filename" to "c\r\nX-Injected: ja\r\n\r\n.pdf",
                "contentType" to "application/pdf\r\nX-Injected: ja",
            ),
        )
        givenAttachment("alleen-regeleinden", mapOf("filename" to "\r\n"))
        givenAttachment(
            "met-parameter",
            mapOf(
                "filename" to "d.pdf",
                "contentType" to "application/pdf; x=\"a\nBcc: kwaad@example.com\"",
            ),
        )
        givenAttachment("multipart", mapOf("filename" to "e.eml", "contentType" to "multipart/mixed"))
        givenAttachment("met-pad", mapOf("filename" to "../../map/f.pdf"))
        givenAttachment("met-windows-pad", mapOf("filename" to "C:\\Users\\jan\\g.pdf"))

        service.sendSmtpMail(
            mailContext(
                content = "<p>Bijlagen</p>",
                attachmentResourceIds =
                    listOf(
                        "leeg",
                        "geen-mime-type",
                        "met-header",
                        "alleen-regeleinden",
                        "met-parameter",
                        "multipart",
                        "met-pad",
                        "met-windows-pad",
                    ),
            ),
            connection,
        )

        assertEquals(
            listOf(
                SmtpMailContentDto.Attachment("a.pdf", "leeg", null),
                SmtpMailContentDto.Attachment("b.pdf", "geen-mime-type", null),
                SmtpMailContentDto.Attachment("c  X-Injected: ja    .pdf", "met-header", null),
                SmtpMailContentDto.Attachment("attachment", "alleen-regeleinden", null),
                SmtpMailContentDto.Attachment("d.pdf", "met-parameter", "application/pdf"),
                SmtpMailContentDto.Attachment("e.eml", "multipart", null),
                SmtpMailContentDto.Attachment("f.pdf", "met-pad", null),
                SmtpMailContentDto.Attachment("g.pdf", "met-windows-pad", null),
            ),
            captureMailContent().attachments,
        )
    }

    @Test
    fun `refuses attachments that together exceed the maximum mail size`() {
        givenAttachment("groot", emptyMap(), size = 20_000_000)
        givenAttachment("nog-een", emptyMap(), size = 5_000_001)

        val exception =
            assertThrows<IllegalStateException> {
                service.sendSmtpMail(
                    mailContext(content = "<p>Te groot</p>", attachmentResourceIds = listOf("groot", "nog-een")),
                    connection,
                )
            }

        assertEquals("Email exceeds max size of 25 mb", exception.message)
        verify(smtpMailClient, never()).sendEmail(any(), any(), any())
    }

    @Test
    fun `takes the size the storage recorded instead of reading the file`() {
        givenAttachment("groot", mapOf("fileSize" to "25000001"))

        assertThrows<IllegalStateException> {
            service.sendSmtpMail(
                mailContext(content = "<p>Te groot</p>", attachmentResourceIds = listOf("groot")),
                connection,
            )
        }

        verify(storageService, never()).getResourceContentAsInputStream("groot")
    }

    private fun givenAttachment(
        id: String,
        metadata: Map<String, Any>,
        size: Int = 4,
    ) {
        whenever(storageService.getResourceContentAsInputStream(id)).doAnswer { ByteArray(size).inputStream() }
        whenever(storageService.getResourceMetadata(id)).doReturn(metadata)
    }

    private fun mailContext(
        contentResourceId: String? = null,
        content: String? = null,
        attachmentResourceIds: List<String> = emptyList(),
    ) = SmtpMailContextDto(
        sender = Email("afzender@example.com"),
        fromName = "Gemeente",
        recipients = listOf(Email("ontvanger@example.com")),
        ccList = emptyList(),
        bccList = emptyList(),
        subject = "Uw aanvraag",
        contentResourceId = contentResourceId,
        attachmentResourceIds = attachmentResourceIds,
        content = content,
    )

    private fun captureMailContent(): SmtpMailContentDto {
        val captor = argumentCaptor<SmtpMailContentDto>()
        verify(smtpMailClient).sendEmail(any(), any(), captor.capture())
        return captor.firstValue
    }

    private val connection =
        SmtpMailPluginPropertyDto(
            host = "smtp.example.com",
            port = 25,
            username = null,
            password = null,
            protocol = "smtp",
            debug = false,
            auth = false,
            startTlsEnable = false,
        )
}
