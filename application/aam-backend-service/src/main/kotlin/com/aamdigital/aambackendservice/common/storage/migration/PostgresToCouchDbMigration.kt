package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.notification.domain.UserDevice
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.beans.factory.ObjectProvider

/**
 * Copies the two pieces of state that cannot be reconstructed out of PostgreSQL and into CouchDB,
 * once, on startup (see #209).
 *
 * - **Push device tokens.** Minted by Firebase on the client, so unreconstructable server-side.
 *   Losing them is not self-healing either: ndb-core checks its registration on load but only
 *   re-registers when the user toggles push in settings, so delivery would silently stop.
 * - **Third-party-auth redirect bindings.** Losing them makes the "go to the external system"
 *   button fail until the user next enters through that system.
 *
 * The change-detection cursor and the SkillLab profile mirror are deliberately not migrated: both
 * rebuild themselves.
 *
 * This is throwaway code. It exists only so the release that stops writing to PostgreSQL can be
 * deployed without losing data; the release that removes the JDBC driver removes this class too.
 *
 * Safe to run repeatedly and safe to run against an instance whose PostgreSQL is already gone: a
 * document that already exists is left alone, a missing table is skipped, and no failure here can
 * stop the service from starting.
 */
class PostgresToCouchDbMigration(
    /**
     * Resolved when the migration runs rather than when it is wired: the DataSource is created by
     * an auto-configuration, which Spring processes after this configuration class.
     */
    private val legacyPostgresSource: () -> LegacyPostgresSource?,
    private val userDeviceRepository: ObjectProvider<UserDeviceRepository>,
    private val thirdPartyAuthSessionRepository: ObjectProvider<ThirdPartyAuthSessionRepository>
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        val source =
            try {
                legacyPostgresSource()
            } catch (ex: Exception) {
                logger.error("[PostgresToCouchDbMigration] could not reach the legacy database", ex)
                return
            }

        if (source == null) {
            logger.info("[PostgresToCouchDbMigration] no datasource configured, nothing to migrate")
            return
        }

        try {
            migrateUserDevices(source)
        } catch (ex: Exception) {
            logger.error("[PostgresToCouchDbMigration] could not migrate push device registrations", ex)
        }

        try {
            migrateRedirectBindings(source)
        } catch (ex: Exception) {
            logger.error("[PostgresToCouchDbMigration] could not migrate third-party-auth redirect bindings", ex)
        }
    }

    private fun migrateUserDevices(source: LegacyPostgresSource) {
        val repository = userDeviceRepository.getIfAvailable() ?: return
        if (!source.tableExists(JdbcLegacyPostgresSource.USER_DEVICE_TABLE)) return

        var migrated = 0
        var skipped = 0

        source.readUserDevices().groupBy { it.userIdentifier }.forEach { (userIdentifier, devices) ->
            val existing = repository.findByUserIdentifier(userIdentifier).map { it.deviceToken }.toSet()

            devices.forEach { device ->
                if (device.deviceToken in existing) {
                    skipped += 1
                } else {
                    repository.addDevice(
                        userIdentifier = userIdentifier,
                        device =
                            UserDevice(
                                deviceToken = device.deviceToken,
                                deviceName = device.deviceName,
                                createdAt = device.createdAt
                            )
                    )
                    migrated += 1
                }
            }
        }

        if (migrated > 0 || skipped > 0) {
            logger.info(
                "[PostgresToCouchDbMigration] push device registrations: {} migrated, {} already present",
                migrated,
                skipped
            )
        }
    }

    private fun migrateRedirectBindings(source: LegacyPostgresSource) {
        val repository = thirdPartyAuthSessionRepository.getIfAvailable() ?: return
        if (!source.tableExists(JdbcLegacyPostgresSource.AUTHENTICATION_SESSION_TABLE)) return

        var migrated = 0
        var skipped = 0

        source.readRedirectBindings().forEach { binding ->
            if (repository.findBySessionId(binding.sessionId) != null) {
                skipped += 1
            } else {
                repository.save(
                    ThirdPartyAuthSession(
                        sessionId = binding.sessionId,
                        userId = binding.userId,
                        redirectUrl = binding.redirectUrl,
                        createdAt = binding.createdAt
                    )
                )
                migrated += 1
            }
        }

        if (migrated > 0 || skipped > 0) {
            logger.info(
                "[PostgresToCouchDbMigration] redirect bindings: {} migrated, {} already present",
                migrated,
                skipped
            )
        }
    }
}
