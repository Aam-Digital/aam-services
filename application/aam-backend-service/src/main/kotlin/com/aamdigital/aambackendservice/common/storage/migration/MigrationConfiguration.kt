package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Wires the one-shot PostgreSQL to CouchDB migration (see [PostgresToCouchDbMigration]).
 *
 * Enabled by default so an upgrade does not have to be configured to be safe. Once a step has
 * completed it is recorded in CouchDB and not run again, so there is no need to switch this off
 * afterwards; `migration.postgres-to-couchdb.enabled: false` skips the migration entirely.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "migration.postgres-to-couchdb",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class MigrationConfiguration {
    @Bean
    fun postgresToCouchDbMigration(
        jdbcTemplate: ObjectProvider<JdbcTemplate>,
        userDeviceRepository: ObjectProvider<UserDeviceRepository>,
        thirdPartyAuthSessionRepository: ObjectProvider<ThirdPartyAuthSessionRepository>,
        couchDbClient: CouchDbClient
    ): PostgresToCouchDbMigration =
        PostgresToCouchDbMigration(
            // an ObjectProvider resolved lazily, because the DataSource comes from an
            // auto-configuration that Spring processes after this class
            legacyPostgresSource = { jdbcTemplate.getIfAvailable()?.let { JdbcLegacyPostgresSource(it) } },
            userDeviceRepository = userDeviceRepository,
            thirdPartyAuthSessionRepository = thirdPartyAuthSessionRepository,
            migrationStateStore = CouchDbMigrationStateStore(couchDbClient)
        )
}
