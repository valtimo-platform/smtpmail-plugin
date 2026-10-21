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

import com.fasterxml.jackson.core.type.TypeReference
import com.ritense.valtimo.contract.json.MapperSingleton
import com.ritense.valtimoplugins.smtpmail.BaseTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourceIdListTest : BaseTest() {
    private val objectMapper = MapperSingleton.get()

    @Test
    fun `binds a single id, the shape of a variable holding one generated file`() {
        assertEquals(ResourceIdList(listOf("file-1")), convert("file-1"))
    }

    @Test
    fun `splits a separated string like the address fields`() {
        assertEquals(ResourceIdList(listOf("file-1", "file-2", "file-3")), convert("file-1, file-2;file-3;"))
    }

    @Test
    fun `binds a list of ids`() {
        assertEquals(ResourceIdList(listOf("file-1", "file-2")), convert(listOf("file-1", "file-2")))
    }

    @Test
    fun `binds a cleared field, as saved by earlier versions of the configuration screen, as no attachments`() {
        assertEquals(ResourceIdList(emptyList()), convert(""))
        assertEquals(ResourceIdList(emptyList()), convert(" "))
    }

    @Test
    fun `drops blank entries from a list`() {
        assertEquals(ResourceIdList(listOf("file-1")), convert(listOf("", " file-1 ")))
    }

    @Test
    fun `rejects an element that is not a string`() {
        assertThrows<IllegalArgumentException> { convert(listOf(42)) }
    }

    @Test
    fun `rejects an object`() {
        assertThrows<IllegalArgumentException> { convert(mapOf("id" to "file-1")) }
    }

    /** The reason ResourceIdList exists; should these ever start passing, drop it for List<String>. */
    @Test
    fun `a plain list of String binds neither a single id nor a cleared field`() {
        listOf("file-1", "").forEach { value ->
            assertThrows<IllegalArgumentException> {
                objectMapper.convertValue(value, object : TypeReference<List<String>>() {})
            }
        }
    }

    private fun convert(value: Any?): ResourceIdList = objectMapper.convertValue(value, ResourceIdList::class.java)
}
