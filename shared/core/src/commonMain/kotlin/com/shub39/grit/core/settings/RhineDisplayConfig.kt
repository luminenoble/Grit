package com.shub39.grit.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
enum class RhineDensity {
    Loose,
    Standard,
    Compact,
    Custom,
}

@Serializable
enum class RhineShadow {
    Off,
    Low,
    Standard,
}

/** Block illustration fitting: fill and crop from the top-left (default), fill centred, or whole. */
@Serializable
enum class RhineImageFit {
    CropTopStart,
    CropCenter,
    Fit,
}

/** Local display preferences only. Never part of the business JSON backup. All lengths are dp. */
@Serializable
data class RhineDisplayConfig(
    val version: Int = 1,
    val density: RhineDensity = RhineDensity.Standard,
    val targetRows: Float = 12f,
    val targetColumns: Float = 3f,
    /** Block title size; the only text on a block, on its long face. */
    val topSp: Float = 8f,
    val length: Float = 207f,
    /** Thickness along a column (the top face's width): thin, like a disc. */
    val depth: Float = 8f,
    val base: Float = 16f,
    val center: Float = 24f,
    val peak: Float = 70f,
    val spread: Float = 4.5f,
    val gapU: Float = 24f,
    val gapV: Float = 12f,
    val categoryNumbers: Boolean = true,
    val taskNumbers: Boolean = true,
    val sectionNumbers: Boolean = true,
    val placeholderNames: Boolean = false,
    val scrollFocusedTitle: Boolean = false,
    val reduceMotion: Boolean = false,
    val shadow: RhineShadow = RhineShadow.Standard,
    /** How block illustrations fill their area; never stretched. */
    val illustrationFit: RhineImageFit = RhineImageFit.CropTopStart,
) {
    fun normalized(): RhineDisplayConfig {
        if (version != 1) return RhineDisplayConfig()
        fun Float.valid(range: ClosedFloatingPointRange<Float>, fallback: Float) =
            if (isFinite() && this in range) this else fallback
        return copy(
            targetRows = targetRows.valid(6f..24f, 12f),
            targetColumns = targetColumns.valid(2f..6f, 3f),
            topSp = topSp.valid(6f..20f, 8f),
            length = length.valid(100f..360f, 207f),
            depth = depth.valid(4f..16f, 8f),
            base = base.valid(8f..48f, 16f),
            center = center.valid(8f..64f, 24f),
            peak = peak.valid(0f..120f, 70f),
            spread = spread.valid(1f..8f, 4.5f),
            gapU = gapU.valid(8f..40f, 24f),
            gapV = gapV.valid(6f..40f, 12f),
        )
    }

    fun preset(value: RhineDensity) =
        when (value) {
            RhineDensity.Loose -> copy(density = value, targetRows = 9f, targetColumns = 2.4f)
            RhineDensity.Standard -> copy(density = value, targetRows = 12f, targetColumns = 3f)
            RhineDensity.Compact -> copy(density = value, targetRows = 16f, targetColumns = 4f)
            RhineDensity.Custom -> copy(density = value)
        }

    fun encode(): String = codec.encodeToString(serializer(), normalized())

    companion object {
        private val codec = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** Unknown schema falls back as a whole; bad individual fields fall back independently. */
        fun decode(raw: String?): RhineDisplayConfig {
            if (raw == null) return RhineDisplayConfig()
            return runCatching {
                    val obj = codec.parseToJsonElement(raw).jsonObject
                    if (obj["version"]?.jsonPrimitive?.intOrNull != 1) return RhineDisplayConfig()
                    val defaults =
                        codec.encodeToJsonElement(serializer(), RhineDisplayConfig()).jsonObject
                    val clean =
                        JsonObject(
                            obj.mapValues { (key, value) ->
                                val default = defaults[key]
                                if (default is JsonPrimitive && value is JsonPrimitive) {
                                    when {
                                        default.booleanOrNull != null ->
                                            if (value.booleanOrNull != null) value else default
                                        default.floatOrNull != null ->
                                            if (value.floatOrNull?.isFinite() == true) value
                                            else default
                                        else -> value
                                    }
                                } else if (default != null && value !is JsonPrimitive) default
                                else value
                            }
                        )
                    codec.decodeFromJsonElement(serializer(), clean).normalized()
                }
                .getOrDefault(RhineDisplayConfig())
        }
    }
}
