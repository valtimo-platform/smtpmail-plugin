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
package com.ritense.valtimoplugins.smtpmail.service

import com.ritense.resource.domain.MetadataType
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimoplugins.smtpmail.client.SmtpMailClient
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContentDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailContextDto
import com.ritense.valtimoplugins.smtpmail.dto.SmtpMailPluginPropertyDto
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.internet.ContentType
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.OutputStream

@SkipComponentScan
@Service
class SmtpMailService(
    private val smtpMailClient: SmtpMailClient,
    private val storageService: TemporaryResourceStorageService,
) {
    fun sendSmtpMail(
        mailContext: SmtpMailContextDto,
        connection: SmtpMailPluginPropertyDto,
    ) {
        val mailContent = prepareMailContent(mailContext)

        smtpMailClient.sendEmail(connection, mailContext, mailContent).also {
            // No subject or addresses: they often carry personal data.
            val recipientCount = with(mailContext) { recipients.size + ccList.size + bccList.size }
            logger.info {
                "Sent SMTP mail to $recipientCount recipient(s) with ${mailContent.attachments.size} attachment(s)"
            }
        }
    }

    private fun prepareMailContent(mailContext: SmtpMailContextDto): SmtpMailContentDto {
        val attachments: MutableList<SmtpMailContentDto.Attachment> = mutableListOf()

        var totalAttachmentsSize = 0L

        mailContext.attachmentResourceIds.forEach { attachmentResourceId ->
            val attachmentMetadata = storageService.getResourceMetadata(attachmentResourceId)
            totalAttachmentsSize += attachmentSize(attachmentResourceId, attachmentMetadata)
            if (totalAttachmentsSize > MAX_SIZE_EMAIL_BODY_IN_BYTES) {
                throw IllegalStateException("Email exceeds max size of 25 mb")
            }

            // Metadata is set by whoever stored the file, so it is cleaned before it ends up in the part's headers.
            val fileName =
                (attachmentMetadata[MetadataType.FILE_NAME.key] as String?)
                    ?.replace(CONTROL_CHARACTERS, " ")
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "attachment"
            // Only type/subtype is kept: parameters can carry line breaks, and without a usable type Spring derives one.
            val contentType = (attachmentMetadata[MetadataType.CONTENT_TYPE.key] as String?)?.toBaseContentType()

            attachments.add(SmtpMailContentDto.Attachment(fileName, attachmentResourceId, contentType))
        }

        return SmtpMailContentDto(
            mailMessage = mailContext.content ?: fetchMailMessage(mailContext.contentResourceId),
            attachments = attachments,
        )
    }

    private fun fetchMailMessage(contentResourceId: String?): String {
        checkNotNull(contentResourceId) { "Mail has neither content nor a contentResourceId" }
        logger.debug { "Fetching mail message with resourceId '$contentResourceId'" }
        return storageService.getResourceContentAsInputStream(contentResourceId).toPlainText()
    }

    // The size the storage recorded saves reading the file twice; without it the file is counted while streaming.
    private fun attachmentSize(
        attachmentResourceId: String,
        attachmentMetadata: Map<String, Any>,
    ): Long =
        attachmentMetadata[MetadataType.FILE_SIZE.key]?.toString()?.toLongOrNull()
            ?: storageService.getResourceContentAsInputStream(attachmentResourceId).use {
                it.transferTo(OutputStream.nullOutputStream())
            }

    // JavaMail would parse a multipart attachment as MIME structure, which fails without the stripped boundary.
    private fun String.toBaseContentType(): String? =
        runCatching { ContentType(this) }
            .getOrNull()
            ?.takeUnless { it.primaryType.equals("multipart", ignoreCase = true) }
            ?.baseType

    private fun InputStream.toPlainText(): String =
        use { stream ->
            stream.bufferedReader().readText()
        }

    companion object {
        val logger = KotlinLogging.logger {}

        private val CONTROL_CHARACTERS = Regex("\\p{Cntrl}")

        private const val MAX_SIZE_EMAIL_BODY_IN_BYTES: Long = 25_000_000 // 25mb
    }
}
