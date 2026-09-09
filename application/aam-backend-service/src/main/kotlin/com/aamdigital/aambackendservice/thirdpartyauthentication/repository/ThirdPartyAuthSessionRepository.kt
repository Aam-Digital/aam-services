package com.aamdigital.aambackendservice.thirdpartyauthentication.repository

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * The durable half of an SSO session: the binding of a sessionId to the user it was issued for and
 * the url that sends them back to the external system.
 *
 * Unlike the login ticket this has no expiry. ndb-core keeps the sessionId in localStorage and its
 * "go to the external system" button reads this binding on every click, indefinitely and across
 * browser restarts, so it has to survive a restart of this service too.
 *
 * A session without a redirect url is still stored, so that asking for its redirect can be
 * answered with "no redirect" rather than "unknown session".
 */
data class ThirdPartyAuthSession(
    val sessionId: String,
    val userId: String,
    val redirectUrl: String? = null,
    /**
     * The shared ObjectMapper leaves WRITE_DATES_AS_TIMESTAMPS enabled, so without this an Instant
     * is stored as a numeric epoch value instead of a readable ISO-8601 string.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val createdAt: Instant? = null
)

interface ThirdPartyAuthSessionRepository {
    fun findBySessionId(sessionId: String): ThirdPartyAuthSession?

    fun save(session: ThirdPartyAuthSession)
}
