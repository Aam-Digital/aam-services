package com.aamdigital.aambackendservice.common.security

/**
 * Utility class for security-related HTTP header operations.
 */
object SecurityHeaderUtils {
    /**
     * Compute the WWW-Authenticate header value according to RFC 6750.
     * @param parameters Map of parameter names to values to include in the header
     * @return The formatted WWW-Authenticate header value
     */
    fun computeWWWAuthenticateHeaderValue(parameters: Map<String, String?>): String {
        val wwwAuthenticate = StringBuilder()
        wwwAuthenticate.append("Bearer")
        if (parameters.isNotEmpty()) {
            wwwAuthenticate.append(" ")
            var i = 0
            for ((key, value) in parameters) {
                wwwAuthenticate.append(key).append("=\"").append(value).append("\"")
                if (i != parameters.size - 1) {
                    wwwAuthenticate.append(", ")
                }
                i++
            }
        }
        return wwwAuthenticate.toString()
    }
}