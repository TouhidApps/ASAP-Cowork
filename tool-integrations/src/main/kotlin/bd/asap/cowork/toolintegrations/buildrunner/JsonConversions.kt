package bd.asap.cowork.toolintegrations.buildrunner

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The tool input/output contract everywhere else in this codebase is a
 * plain `Map<String, Any?>` (see [bd.asap.cowork.llmgateway.ToolExecutor]),
 * because it's built from a provider SDK's own loosely-typed tool-call
 * arguments. Crossing the HTTP boundary to build-runner means round-
 * tripping that same shape through JSON without inventing a parallel typed
 * schema per tool — these two functions are the only place that happens.
 */
fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
    forEach { (key, value) -> put(key, value.toJsonElement()) }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject { forEach { (k, v) -> put(k.toString(), v.toJsonElement()) } }
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

fun JsonObject.toKotlinMap(): Map<String, Any?> = mapValues { (_, value) -> value.toKotlinValue() }

private fun JsonElement.toKotlinValue(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> toKotlinMap()
    is JsonArray -> map { it.toKotlinValue() }
    is JsonPrimitive -> when {
        !isString && booleanOrNull != null -> booleanOrNull
        !isString && longOrNull != null -> longOrNull
        !isString && doubleOrNull != null -> doubleOrNull
        else -> content
    }
}
