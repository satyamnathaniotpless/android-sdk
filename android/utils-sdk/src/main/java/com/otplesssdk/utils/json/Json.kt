package com.otplesssdk.utils.json

/**
 * Minimal JSON utilities used across SDK modules.
 *
 * - No external dependency
 * - Always emits valid JSON (strings are quoted and escaped)
 * - Supports nested maps/lists/arrays
 */
internal object Json {
    /**
     * Wrapper for embedding already-serialized JSON into a larger payload.
     *
     * This is intentionally low-level: the caller is responsible for ensuring [json] is valid JSON.
     */
    internal data class Raw(val json: String)

    /**
     * Quotes and escapes a JSON string value.
     *
     * Handles:
     * - Control characters (U+0000..U+001F)
     * - Quotes and backslashes
     * - Unpaired surrogate halves (escaped as \\uXXXX)
     * - Supplementary Unicode code points (preserved via surrogate pairs)
     */
    fun quote(value: String): String {
        val sb = StringBuilder(value.length + 10)
        sb.append('"')
        var i = 0
        while (i < value.length) {
            val codePoint = value.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            when (codePoint) {
                '\\'.code -> sb.append("\\\\")
                '"'.code -> sb.append("\\\"")
                '\n'.code -> sb.append("\\n")
                '\r'.code -> sb.append("\\r")
                '\t'.code -> sb.append("\\t")
                '\b'.code -> sb.append("\\b")
                0x000C -> sb.append("\\f") // Form feed
                else -> {
                    // Escape control characters (U+0000..U+001F) and unpaired surrogate halves.
                    if (codePoint < 0x20 || (codePoint in 0xD800..0xDFFF)) {
                        sb.append("\\u")
                        sb.append(String.format("%04x", codePoint))
                    } else {
                        sb.append(Character.toChars(codePoint))
                    }
                }
            }
            i += charCount
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Converts a Kotlin value to JSON.
     *
     * Supports: null, booleans, numbers, strings, maps, iterables, arrays.
     * All other types are stringified via toString() and emitted as JSON strings.
     */
    fun value(v: Any?): String {
        return when (v) {
            null -> "null"
            is Boolean -> v.toString()
            is Number -> when (v) {
                // Only Float/Double can be non-finite, which is invalid JSON.
                is Double -> if (!v.isNaN() && !v.isInfinite()) v.toString() else "null"
                is Float -> if (!v.isNaN() && !v.isInfinite()) v.toString() else "null"
                else -> v.toString()
            }
            is String -> quote(v)
            is Raw -> v.json
            is Map<*, *> -> obj(v)
            is Iterable<*> -> arr(v)
            is Array<*> -> arr(v.asList())
            else -> quote(v.toString())
        }
    }

    private fun obj(map: Map<*, *>): String {
        val entries = map.entries.joinToString(",") { (k, v) ->
            val keyString = k?.toString() ?: "null"
            "${quote(keyString)}:${value(v)}"
        }
        return "{$entries}"
    }

    private fun arr(values: Iterable<*>): String {
        val entries = values.joinToString(",") { item -> value(item) }
        return "[$entries]"
    }
}

