package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.notification.repository.UserDeviceEntity
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.time.ZoneOffset

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
 * Within a step, a document that already exists is left alone. A row that cannot be copied is
 * logged and dropped, and the step still counts as completed: running it again would re-copy every
 * row and so bring back what was deleted since. Only a step that copied nothing because every row
 * failed (CouchDB is down, say) stays pending, since there is nothing it could bring back. No failure
 * here can stop the service from starting.
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

    /** Rows copied, rows already present, and rows that could not be copied. */
    private data class StepResult(
        val migrated: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0
    )

    /** @param migrate returns null if there is no legacy table to copy from */
    private fun runStep(
        step: String,
        description: String,
        migrate: () -> StepResult?
    ) {
        try {
            val result = migrate()
            if (result != null) {
                logger.info(
                    "[PostgresToCouchDbMigration] {}: {} migrated, {} already present, {} failed",
                    description,
                    result.migrated,
                    result.skipped,
                    result.failed
                )
                if (result.migrated == 0 && result.failed > 0) return
            }
            migrationStateStore.markCompleted(step)
        } catch (ex: Exception) {
            logger.error("[PostgresToCouchDbMigration] could not migrate {}", description, ex)
        }
    }

    /**
     * Copies each row that is not [present] yet, isolating failures to the row.
     *
     * @param describe names a row in the log without exposing a secret like a device token
     */
    private fun <T> copyRows(
        rows: List<T>,
        describe: (T) -> String,
        present: (T) -> Boolean,
        copy: (T) -> Unit
    ): StepResult =
        rows.fold(StepResult()) { result, row ->
            try {
                if (present(row)) {
                    result.copy(skipped = result.skipped + 1)
                } else {
                    copy(row)
                    result.copy(migrated = result.migrated + 1)
                }
            } catch (ex: Exception) {
                logger.error("[PostgresToCouchDbMigration] could not copy {}", describe(row), ex)
                result.copy(failed = result.failed + 1)
            }
        }

    private fun migrateUserDevices(
        source: LegacyPostgresSource,
        repository: UserDeviceRepository
    ): StepResult? {
        if (!source.tableExists(JdbcLegacyPostgresSource.USER_DEVICE_TABLE)) return null

        return copyRows(
            rows = source.readUserDevices(),
            describe = { "a push device registration of user ${it.userIdentifier}" },
            present = { repository.existsByDeviceToken(it.deviceToken) },
            copy = { device ->
                repository.save(
                    UserDeviceEntity(
                        userIdentifier = device.userIdentifier,
                        deviceToken = device.deviceToken,
                        deviceName = device.deviceName,
                        createdAt = device.createdAt?.atOffset(ZoneOffset.UTC)
                    )
                )
            }
        )
    }

    private fun migrateRedirectBindings(
        source: LegacyPostgresSource,
        repository: ThirdPartyAuthSessionRepository
    ): StepResult? {
        if (!source.tableExists(JdbcLegacyPostgresSource.AUTHENTICATION_SESSION_TABLE)) return null

        return copyRows(
            rows = source.readRedirectBindings(),
            describe = { "a redirect binding of user ${it.userId}" },
            present = { repository.findBySessionId(it.sessionId) != null },
            copy = { binding ->
                repository.save(
                    ThirdPartyAuthSession(
                        sessionId = binding.sessionId,
                        userId = binding.userId,
                        redirectUrl = binding.redirectUrl,
                        createdAt = binding.createdAt
                    )
                )
            }
        )
    }
}
