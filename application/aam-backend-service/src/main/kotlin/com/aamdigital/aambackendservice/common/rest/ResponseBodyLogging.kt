package com.aamdigital.aambackendservice.common.rest

/**
 * Maximum number of characters of a remote response body to include in logs or error messages.
 *
 * Remote responses are read as a raw String first so the actual payload survives a
 * parsing failure and can be surfaced for debugging. Bodies can be large or unbounded, so any value
 * that reaches a log line or exception message must be capped to this length.
 */
const val MAX_LOGGED_RESPONSE_BODY_LENGTH = 500

/**
 * Truncate a (possibly large or null) remote response body to a bounded length so it can be safely
 * included in logs or exception messages without dumping an unbounded payload.
 */
fun String?.truncateForLog(maxLength: Int = MAX_LOGGED_RESPONSE_BODY_LENGTH): String {
    val value = this.orEmpty()
    return if (value.length <= maxLength) value else value.take(maxLength) + "… (truncated)"
}
