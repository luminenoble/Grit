/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.core.tasks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** A named, editable part of a task or habit. Completion is used by tasks. */
@Serializable
data class Section(val name: String, val content: String = "", val done: Boolean = false)

/** Reads legacy string steps and text/done steps, assigning names in their original order. */
object SectionsSerializer : KSerializer<List<Section>> {
    private val delegate = ListSerializer(Section.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Section>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<Section> {
        val jsonDecoder =
            decoder as? JsonDecoder ?: throw SerializationException("Sections support JSON only")
        val array =
            jsonDecoder.decodeJsonElement() as? JsonArray
                ?: throw SerializationException("Expected a sections array")
        return array.mapIndexed { index, item ->
            when (item) {
                is JsonPrimitive -> {
                    if (!item.isString) throw SerializationException("Expected section text")
                    Section(name = "section ${index + 1}", content = item.content)
                }
                is JsonObject ->
                    Section(
                        name =
                            (item["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                                ?: "section ${index + 1}",
                        content =
                            ((item["content"] ?: item["text"]) as? JsonPrimitive)?.content ?: "",
                        done = (item["done"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    )
                else -> throw SerializationException("Invalid section")
            }
        }
    }
}

/** Do not reuse a remaining default name after deleting or rearranging sections. */
fun nextSectionName(sections: List<Section>): String {
    val last =
        sections
            .mapNotNull {
                Regex("section (\\d+)").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
            }
            .maxOrNull() ?: 0
    return "section ${maxOf(last, sections.size) + 1}"
}
