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
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A single sub-step of a task.
 *
 * @param text : what to do
 * @param done : whether this step has been checked off
 */
@Serializable(with = TaskStepSerializer::class)
data class TaskStep(val text: String, val done: Boolean = false)

/**
 * Steps used to be stored as plain JSON strings; this serializer reads both the old `"text"` and
 * the new `{"text": ..., "done": ...}` forms so existing rows and backups keep loading.
 */
object TaskStepSerializer : KSerializer<TaskStep> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("TaskStep") {
            element<String>("text")
            element<Boolean>("done")
        }

    override fun serialize(encoder: Encoder, value: TaskStep) {
        val jsonEncoder =
            encoder as? JsonEncoder ?: throw SerializationException("TaskStep supports JSON only")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("text", value.text)
                put("done", value.done)
            }
        )
    }

    override fun deserialize(decoder: Decoder): TaskStep {
        val jsonDecoder =
            decoder as? JsonDecoder ?: throw SerializationException("TaskStep supports JSON only")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonObject ->
                TaskStep(
                    text = (element["text"] as? JsonPrimitive)?.content ?: "",
                    done = (element["done"] as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            is JsonPrimitive -> TaskStep(text = element.content)
            else -> throw SerializationException("Unexpected TaskStep JSON: $element")
        }
    }
}
