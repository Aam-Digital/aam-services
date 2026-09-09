package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import java.time.OffsetDateTime

/**
 * A one-time login ticket handed to an externally authenticated user.
 *
 * Short-lived by construction: it is valid for minutes and can be redeemed once. Once redeemed,
 * Keycloak issues its own session and JWT and the ticket plays no further part in authentication,
 * which is why it is not persisted at all.
 */
data class AuthenticationSession(
    val sessionId: String,
    val userId: String,
    val externalUserId: String,
    val sessionTokenHash: String,
    val validUntil: OffsetDateTime,
    val usedAt: OffsetDateTime? = null
)

interface AuthenticationSessionStore {
    fun store(session: AuthenticationSession)

    fun find(sessionId: String): AuthenticationSession?

    fun markUsed(
        sessionId: String,
        usedAt: OffsetDateTime
    )
}

/**
 * In-memory [AuthenticationSessionStore].
 *
 * A ticket is created and redeemed within the same process (the Keycloak SPI calls straight back
 * into this service), so memory is sufficient - as long as the service runs as a single replica.
 * A restart during the few minutes a ticket is valid makes the user re-enter through the external
 * system; a *redeemed* ticket is unaffected, since nobody is logged out by losing it.
 *
 * Entries are retained past [sessionValidity] on purpose. Eviction is only there to reclaim
 * memory: while the entry is still around, [DefaultVerifySessionUseCase] can compare against
 * [AuthenticationSession.validUntil] and answer SESSION_EXPIRED, which a vanished entry could only
 * report as an unknown session.
 */
class CaffeineAuthenticationSessionStore(
    sessionValidity: Duration,
    retentionFactor: Long = RETENTION_FACTOR
) : AuthenticationSessionStore {
    companion object {
        private const val RETENTION_FACTOR = 4L
        private const val MAX_SESSIONS = 10_000L
    }

    private val sessions =
        Caffeine
            .newBuilder()
            .expireAfterWrite(sessionValidity.multipliedBy(retentionFactor))
            .maximumSize(MAX_SESSIONS)
            .build<String, AuthenticationSession>()

    override fun store(session: AuthenticationSession) {
        sessions.put(session.sessionId, session)
    }

    override fun find(sessionId: String): AuthenticationSession? = sessions.getIfPresent(sessionId)

    override fun markUsed(
        sessionId: String,
        usedAt: OffsetDateTime
    ) {
        val session = sessions.getIfPresent(sessionId) ?: return
        sessions.put(sessionId, session.copy(usedAt = usedAt))
    }
}
