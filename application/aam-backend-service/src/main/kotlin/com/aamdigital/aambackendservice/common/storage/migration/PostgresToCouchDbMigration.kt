package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.notification.domain.UserDevice
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * Copies the two pieces of state that cannot be reconstructed out of PostgreSQL and into CouchDB,
 * once, on startup.
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
 * Each step runs until it has completed once, which is recorded in [MigrationStateStore]; after
 * that it never reads PostgreSQL again. That matters because PostgreSQL is no longer written to:
 * re-copying on every start would bring back devices users have unregistered since. A step is
 * only attempted while its module is enabled, so enabling a module later still migrates its data.
 * Within a step, a document that already exists is left alone, so a step that failed halfway can
 * simply run again on the next start. No failure here can stop the service from starting.
 */
class PostgresToCouchDbMigration(
    /**
     * Resolved when the migration runs rather than when it is wired: the DataSource is created by
     * an auto-configuration, which Spring processes after this configuration class.
     */
    private val legacyPostgresSource: () -> LegacyPostgresSource?,
    private val userDeviceRepository: ObjectProvider<UserDeviceRepository>,
    private val thirdPartyAuthSessionRepository: ObjectProvider<ThirdPartyAuthSessionRepository>,
    private val migrationStateStore: MigrationStateStore
) : ApplicationRunner {
    companion object {
        const val USER_DEVICES_STEP = "postgres-to-couchdb:user-devices"
        const val REDIRECT_BINDINGS_STEP = "postgres-to-couchdb:redirect-bindings"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        val deviceRepository = userDeviceRepository.getIfAvailable()?.takeIf { isPending(USER_DEVICES_STEP) }
        val sessionRepository =
            thirdPartyAuthSessionRepository.getIfAvailable()?.takeIf { isPending(REDIRECT_BINDINGS_STEP) }

        if (deviceRepository == null && sessionRepository == null) return

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

        deviceRepository?.let { repository ->
            runStep(USER_DEVICES_STEP, "push device registrations") { migrateUserDevices(source, repository) }
        }

        sessionRepository?.let { repository ->
            runStep(REDIRECT_BINDINGS_STEP, "third-party-auth redirect bindings") {
                migrateRedirectBindings(source, repository)
            }
        }
    }

    private fun isPending(step: String): Boolean =
        try {
            !migrationStateStore.isCompleted(step)
        } catch (ex: Exception) {
            logger.error("[PostgresToCouchDbMigration] could not read the state of step {}", step, ex)
            false
        }

    private fun runStep(
        step: String,
        description: String,
        migrate: () -> Unit
    ) {
        try {
            migrate()
            migrationStateStore.markCompleted(step)
        } catch (ex: Exception) {
            logger.error("[PostgresToCouchDbMigration] could not migrate {}", description, ex)
        }
    }

    private fun migrateUserDevices(
        source: LegacyPostgresSource,
        repository: UserDeviceRepository
    ) {
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

        logger.info(
            "[PostgresToCouchDbMigration] push device registrations: {} migrated, {} already present",
            migrated,
            skipped
        )
    }

    private fun migrateRedirectBindings(
        source: LegacyPostgresSource,
        repository: ThirdPartyAuthSessionRepository
    ) {
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

        logger.info(
            "[PostgresToCouchDbMigration] redirect bindings: {} migrated, {} already present",
            migrated,
            skipped
        )
    }
}
