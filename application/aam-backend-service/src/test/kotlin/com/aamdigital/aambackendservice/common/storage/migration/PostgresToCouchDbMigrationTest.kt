package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.notification.repository.UserDeviceEntity
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

/**
 * The migration runs once, against real production data, and cannot be exercised by the e2e suite
 * because the tables it reads no longer exist there - so its safety properties are pinned here.
 */
class PostgresToCouchDbMigrationTest {
    private lateinit var devices: FakeUserDeviceRepository
    private lateinit var sessions: FakeThirdPartyAuthSessionRepository
    private lateinit var state: FakeMigrationStateStore

    private class FakeUserDeviceRepository : UserDeviceRepository {
        val byToken = mutableMapOf<String, UserDeviceEntity>()

        fun tokensOf(userIdentifier: String) =
            byToken.values.filter { it.userIdentifier == userIdentifier }.map { it.deviceToken }

        override fun findByUserIdentifier(
            userIdentifier: String,
            pageable: Pageable
        ) = PageImpl(byToken.values.filter { it.userIdentifier == userIdentifier })

        override fun findByDeviceToken(deviceToken: String) = Optional.ofNullable(byToken[deviceToken])

        override fun existsByDeviceToken(deviceToken: String) = deviceToken in byToken

        override fun deleteByDeviceToken(deviceToken: String) {
            byToken.remove(deviceToken)
        }

        override fun save(userDevice: UserDeviceEntity) {
            check(userDevice.deviceToken !in byToken) { "device token already registered" }
            byToken[userDevice.deviceToken] = userDevice
        }
    }

    private class FakeThirdPartyAuthSessionRepository : ThirdPartyAuthSessionRepository {
        val stored = mutableMapOf<String, ThirdPartyAuthSession>()

        override fun findBySessionId(sessionId: String) = stored[sessionId]

        override fun save(session: ThirdPartyAuthSession) {
            stored[session.sessionId] = session
        }
    }

    private class FakeMigrationStateStore : MigrationStateStore {
        val completed = mutableSetOf<String>()

        override fun isCompleted(step: String) = step in completed

        override fun markCompleted(step: String) {
            completed.add(step)
        }
    }

    private class FakeSource(
        private val tables: Set<String> =
            setOf(
                JdbcLegacyPostgresSource.USER_DEVICE_TABLE,
                JdbcLegacyPostgresSource.AUTHENTICATION_SESSION_TABLE
            ),
        private val userDevices: List<LegacyUserDevice> = emptyList(),
        private val redirectBindings: List<LegacyRedirectBinding> = emptyList()
    ) : LegacyPostgresSource {
        override fun tableExists(table: String) = table in tables

        override fun readUserDevices() = userDevices

        override fun readRedirectBindings() = redirectBindings
    }

    /** A minimal ObjectProvider that always yields the given value. */
    private class Provider<T : Any>(
        private val value: T?
    ) : ObjectProvider<T> {
        override fun getObject(vararg args: Any?): T = value!!

        override fun getObject(): T = value!!

        override fun getIfAvailable(): T? = value

        override fun getIfUnique(): T? = value
    }

    private fun migration(
        source: LegacyPostgresSource?,
        userDeviceRepository: UserDeviceRepository? = devices,
        thirdPartyAuthSessionRepository: ThirdPartyAuthSessionRepository? = sessions
    ) = PostgresToCouchDbMigration(
        legacyPostgresSource = { source },
        userDeviceRepository = Provider(userDeviceRepository),
        thirdPartyAuthSessionRepository = Provider(thirdPartyAuthSessionRepository),
        migrationStateStore = state
    )

    @BeforeEach
    fun setUp() {
        devices = FakeUserDeviceRepository()
        sessions = FakeThirdPartyAuthSessionRepository()
        state = FakeMigrationStateStore()
    }

    @Test
    fun `copies device registrations`() {
        val createdAt = Instant.parse("2024-01-01T00:00:00Z")
        migration(
            FakeSource(
                userDevices =
                    listOf(
                        LegacyUserDevice("user-1", "token-a", "Phone", createdAt),
                        LegacyUserDevice("user-1", "token-b", "Tablet", createdAt),
                        LegacyUserDevice("user-2", "token-c", null, null)
                    )
            )
        ).run(null)

        assertThat(devices.tokensOf("user-1")).containsExactly("token-a", "token-b")
        val firstDevice = devices.byToken.getValue("token-a")
        assertThat(firstDevice.deviceName).isEqualTo("Phone")
        assertThat(firstDevice.createdAt).isEqualTo(createdAt.atOffset(ZoneOffset.UTC))
        assertThat(devices.tokensOf("user-2")).containsExactly("token-c")
    }

    @Test
    fun `copies only redirect bindings and keeps the session id`() {
        migration(
            FakeSource(
                redirectBindings =
                    listOf(
                        LegacyRedirectBinding("session-1", "user-1", "https://external/1", null)
                    )
            )
        ).run(null)

        assertThat(sessions.stored.keys).containsExactly("session-1")
        assertThat(sessions.stored.getValue("session-1").userId).isEqualTo("user-1")
        assertThat(sessions.stored.getValue("session-1").redirectUrl).isEqualTo("https://external/1")
    }

    @Test
    fun `is idempotent`() {
        val source =
            FakeSource(
                userDevices = listOf(LegacyUserDevice("user-1", "token-a", "Phone", null)),
                redirectBindings = listOf(LegacyRedirectBinding("session-1", "user-1", "https://external/1", null))
            )

        migration(source).run(null)
        migration(source).run(null)

        assertThat(devices.byToken).hasSize(1)
        assertThat(sessions.stored).hasSize(1)
    }

    @Test
    fun `records each completed step`() {
        migration(FakeSource()).run(null)

        assertThat(state.completed).containsExactlyInAnyOrder(
            PostgresToCouchDbMigration.USER_DEVICES_STEP,
            PostgresToCouchDbMigration.REDIRECT_BINDINGS_STEP
        )
    }

    /**
     * PostgreSQL is no longer written to, so an unregistration only reaches CouchDB. Copying again
     * on the next start would bring the device back.
     */
    @Test
    fun `does not bring back a device unregistered after the migration`() {
        val source = FakeSource(userDevices = listOf(LegacyUserDevice("user-1", "token-a", "Phone", null)))

        migration(source).run(null)
        devices.deleteByDeviceToken("token-a")
        migration(source).run(null)

        assertThat(devices.tokensOf("user-1")).isEmpty()
    }

    @Test
    fun `does not touch the legacy database once every step completed`() {
        state.completed.addAll(
            listOf(PostgresToCouchDbMigration.USER_DEVICES_STEP, PostgresToCouchDbMigration.REDIRECT_BINDINGS_STEP)
        )
        var sourceResolved = false

        PostgresToCouchDbMigration(
            legacyPostgresSource = {
                sourceResolved = true
                FakeSource()
            },
            userDeviceRepository = Provider(devices),
            thirdPartyAuthSessionRepository = Provider(sessions),
            migrationStateStore = state
        ).run(null)

        assertThat(sourceResolved).isFalse()
    }

    @Test
    fun `leaves a step pending while its module is disabled`() {
        val source = FakeSource(userDevices = listOf(LegacyUserDevice("user-1", "token-a", "Phone", null)))

        migration(source, userDeviceRepository = null).run(null)
        assertThat(state.completed).doesNotContain(PostgresToCouchDbMigration.USER_DEVICES_STEP)

        // the module is enabled on a later start
        migration(source).run(null)
        assertThat(devices.tokensOf("user-1")).containsExactly("token-a")
    }

    /** A device repository that rejects every save for the given users. */
    private fun repositoryFailingFor(vararg userIdentifiers: String) =
        object : UserDeviceRepository by devices {
            val failFor = userIdentifiers.toMutableSet()

            override fun save(userDevice: UserDeviceEntity) {
                if (userDevice.userIdentifier in failFor) throw IllegalStateException("couchdb rejected it")
                devices.save(userDevice)
            }
        }

    /**
     * Running the step again would re-copy every row, bringing back devices unregistered since, so
     * a row that cannot be copied is dropped rather than retried.
     */
    @Test
    fun `drops a row that cannot be copied and completes the step`() {
        val source =
            FakeSource(
                userDevices =
                    listOf(
                        LegacyUserDevice("user-1", "token-a", "Phone", null),
                        LegacyUserDevice("user-2", "token-b", "Tablet", null),
                        LegacyUserDevice("user-3", "token-c", "Laptop", null)
                    )
            )

        migration(source, userDeviceRepository = repositoryFailingFor("user-2")).run(null)

        assertThat(devices.byToken.keys).containsExactlyInAnyOrder("token-a", "token-c")
        assertThat(state.completed).contains(PostgresToCouchDbMigration.USER_DEVICES_STEP)
    }

    /** A step that wrote nothing cannot bring anything back, so it is safe to retry. */
    @Test
    fun `leaves a step pending when every row failed`() {
        val source =
            FakeSource(
                userDevices =
                    listOf(
                        LegacyUserDevice("user-1", "token-a", "Phone", null),
                        LegacyUserDevice("user-2", "token-b", "Tablet", null)
                    )
            )

        migration(source, userDeviceRepository = repositoryFailingFor("user-1", "user-2")).run(null)
        assertThat(state.completed).doesNotContain(PostgresToCouchDbMigration.USER_DEVICES_STEP)

        // CouchDB is back on the next start
        migration(source).run(null)

        assertThat(devices.byToken.keys).containsExactlyInAnyOrder("token-a", "token-b")
        assertThat(state.completed).contains(PostgresToCouchDbMigration.USER_DEVICES_STEP)
    }

    @Test
    fun `does nothing when the tables are gone`() {
        migration(
            FakeSource(
                tables = emptySet(),
                userDevices = listOf(LegacyUserDevice("user-1", "token-a", null, null)),
                redirectBindings = listOf(LegacyRedirectBinding("session-1", "user-1", "https://external/1", null))
            )
        ).run(null)

        assertThat(devices.byToken).isEmpty()
        assertThat(sessions.stored).isEmpty()
    }

    @Test
    fun `does nothing when no datasource is configured`() {
        migration(null).run(null)

        assertThat(devices.byToken).isEmpty()
        assertThat(sessions.stored).isEmpty()
    }

    @Test
    fun `a failure while reading does not propagate`() {
        val failing =
            object : LegacyPostgresSource {
                override fun tableExists(table: String) = true

                override fun readUserDevices(): List<LegacyUserDevice> = throw IllegalStateException("db is gone")

                override fun readRedirectBindings(): List<LegacyRedirectBinding> =
                    throw IllegalStateException("db is gone")
            }

        migration(failing).run(null)

        assertThat(devices.byToken).isEmpty()
        assertThat(state.completed).isEmpty()
    }
}
