package com.aamdigital.aambackendservice.common.storage.migration

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant

/** A row of the retired `user_device_entity` table. */
data class LegacyUserDevice(
    val userIdentifier: String,
    val deviceToken: String,
    val deviceName: String?,
    val createdAt: Instant?
)

/** The durable half of a row of the retired `authentication_session_entity` table. */
data class LegacyRedirectBinding(
    val sessionId: String,
    val userId: String,
    val redirectUrl: String,
    val createdAt: Instant?
)

/**
 * Read access to the tables this service used to own, for [PostgresToCouchDbMigration].
 *
 * Behind an interface so the migration can be tested without a database: it runs once, against
 * real production data, and cannot be exercised by the e2e suite because these tables no longer
 * exist there.
 */
interface LegacyPostgresSource {
    fun tableExists(table: String): Boolean

    fun readUserDevices(): List<LegacyUserDevice>

    fun readRedirectBindings(): List<LegacyRedirectBinding>
}

class JdbcLegacyPostgresSource(
    private val jdbcTemplate: JdbcTemplate
) : LegacyPostgresSource {
    companion object {
        const val USER_DEVICE_TABLE = "user_device_entity"
        const val AUTHENTICATION_SESSION_TABLE = "authentication_session_entity"
    }

    override fun tableExists(table: String): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
            Int::class.java,
            table
        ) != 0

    override fun readUserDevices(): List<LegacyUserDevice> =
        jdbcTemplate.query(
            "SELECT user_identifier, device_token, device_name, created_at FROM $USER_DEVICE_TABLE"
        ) { rs, _ ->
            LegacyUserDevice(
                userIdentifier = rs.getString("user_identifier"),
                deviceToken = rs.getString("device_token"),
                deviceName = rs.getString("device_name"),
                createdAt = rs.getTimestamp("created_at")?.toInstantOrNull()
            )
        }

    /**
     * Only rows that actually carry a redirect url. The login-ticket half of these rows is
     * deliberately not migrated: it expires within minutes, so re-login is the correct outcome.
     */
    override fun readRedirectBindings(): List<LegacyRedirectBinding> =
        jdbcTemplate.query(
            "SELECT external_identifier, user_id, redirect_url, created_at " +
                "FROM $AUTHENTICATION_SESSION_TABLE " +
                "WHERE redirect_url IS NOT NULL AND redirect_url <> ''"
        ) { rs, _ ->
            LegacyRedirectBinding(
                sessionId = rs.getString("external_identifier"),
                userId = rs.getString("user_id"),
                redirectUrl = rs.getString("redirect_url"),
                createdAt = rs.getTimestamp("created_at")?.toInstantOrNull()
            )
        }

    private fun Timestamp.toInstantOrNull(): Instant? = this.toInstant()
}
