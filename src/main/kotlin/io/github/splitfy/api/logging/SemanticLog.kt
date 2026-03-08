package io.github.splitfy.api.logging

import org.slf4j.Logger

data class EmailLogContext(
    val event: String,
    val entity: String,
    val entityId: Any? = null,
    val entityToken: Any? = null,
    val metadata: Map<String, Any?> = emptyMap(),
)

fun Logger.infoEvent(event: String, vararg fields: Pair<String, Any?>) {
    info(buildEventMessage(event, fields.asList()))
}

fun Logger.warnEvent(event: String, vararg fields: Pair<String, Any?>) {
    warn(buildEventMessage(event, fields.asList()))
}

fun Logger.warnEvent(event: String, throwable: Throwable, vararg fields: Pair<String, Any?>) {
    warn(buildEventMessage(event, fields.asList()), throwable)
}

fun Logger.errorEvent(event: String, throwable: Throwable, vararg fields: Pair<String, Any?>) {
    error(buildEventMessage(event, fields.asList()), throwable)
}

private fun buildEventMessage(event: String, fields: List<Pair<String, Any?>>): String {
    return buildString {
        append("event=").append(event)
        fields.forEach { (key, value) ->
            if (value == null) {
                return@forEach
            }
            append(' ')
            append(key)
            append('=')
            append(sanitize(value))
        }
    }
}

private fun sanitize(value: Any): String {
    return value.toString()
        .replace('\n', '_')
        .replace('\r', '_')
        .replace(' ', '_')
}
