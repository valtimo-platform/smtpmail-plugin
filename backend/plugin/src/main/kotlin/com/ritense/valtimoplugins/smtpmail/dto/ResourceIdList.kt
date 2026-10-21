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

package com.ritense.valtimoplugins.smtpmail.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode

/** The ids of the attachments field, accepting a single or separated id, a cleared field or a list. */
data class ResourceIdList(
    @get:JsonValue val ids: List<String>,
) {
    companion object {
        // The same separators as the address fields, so one text field can carry several ids.
        private val ID_SEPARATOR = Regex("[,;]")

        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun of(node: JsonNode): ResourceIdList =
            when {
                node.isNull -> ResourceIdList(emptyList())
                node.isTextual -> ResourceIdList(node.textValue().split(ID_SEPARATOR).withoutBlanks())
                node.isArray -> ResourceIdList(node.map { it.textValueOrFail() }.withoutBlanks())
                else -> throw IllegalArgumentException("Expected a file id or a list of file ids, but got: $node")
            }

        private fun List<String>.withoutBlanks() = map { it.trim() }.filter { it.isNotEmpty() }

        private fun JsonNode.textValueOrFail(): String {
            require(isTextual) { "Expected a file id, but got: $this" }
            return textValue()
        }
    }
}
