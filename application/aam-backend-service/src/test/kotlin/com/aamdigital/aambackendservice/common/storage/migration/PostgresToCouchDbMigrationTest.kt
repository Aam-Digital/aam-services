package com.aamdigital.aambackendservice.common.storage.migration

import com.aamdigital.aambackendservice.notification.domain.UserDevice
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

/**
 * The migration runs once, against real production data, and cannot be exercised by the e2e suite
 * because the tables it reads no longer exist there - so its safety properties are pinned here.
 */
class PostgresToCouchDbMigrationTest {
    private lateinit var devices: FakeUserDeviceRepository
    private lateinit var sessions: FakeThirdPartyAuthSessionRepository
    private lateinit var state: FakeMigrationStateStore

    private class FakeUserDeviceRepository : UserDeviceRepository {
        val byUser = mutableMapOf<String, MutableList<UserDevice>>()

        override fun findByUserIdentifier(userIdentifier: String) = byUser[userIdentifier].orEmpty()

        override fun findDevice(
            userIdentifier: String,
            deviceToken: String
        ) = findByUserIdentifier(userIdentifier).firstOrNull { it.deviceToken == deviceToken }

        override fun addDevice(
            userIdentifier: String,
            device: UserDevice
        ) {
            byUser.getOrPut(userIdentifier) { mutableListOf() }.add(device)
        }

        override fun removeDevice(
            userIdentifier: String,
            deviceToken: String
        ) {
            byUser[userIdentifier]?.removeIf { it.deviceToken == deviceToken }
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
    fun `copies device registrations grouped by user`() {
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

        assertThat(devices.byUser.getValue("user-1").map { it.deviceToken })
            .containsExactly("token-a", "token-b")
        val firstDevice = devices.byUser.getValue("user-1").first()
        assertThat(firstDevice.deviceName).isEqualTo("Phone")
        assertThat(firstDevice.createdAt).isEqualTo(createdAt)
        assertThat(devices.byUser.getValue("user-2").map { it.deviceToken }).containsExactly("token-c")
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

        assertThat(devices.byUser.getValue("user-1")).hasSize(1)
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
        devices.removeDevice("user-1", "token-a")
        migration(source).run(null)

        assertThat(devices.findByUserIdentifier("user-1")).isEmpty()
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
        assertThat(devices.findByUserIdentifier("user-1").map { it.deviceToken }).containsExactly("token-a")
    }

    @Test
    fun `a failed step stays pending and skips what an earlier attempt already copied`() {
        val rows =
            listOf(
                LegacyUserDevice("user-1", "token-a", "Phone", null),
                LegacyUserDevice("user-2", "token-b", "Tablet", null)
            )
        var failAfterFirstUser = true
        val flaky =
            object : LegacyPostgresSource {
                override fun tableExists(table: String) = true

                override fun readUserDevices() = rows

                override fun readRedirectBindings() = emptyList<LegacyRedirectBinding>()
            }
        val failingRepository =
            object : UserDeviceRepository by devices {
                override fun addDevice(
                    userIdentifier: String,
                    device: UserDevice
                ) {
                    if (failAfterFirstUser && userIdentifier == "user-2") throw IllegalStateException("couchdb is gone")
                    devices.addDevice(userIdentifier, device)
                }
            }

        migration(flaky, userDeviceRepository = failingRepository).run(null)
        assertThat(state.completed).doesNotContain(PostgresToCouchDbMigration.USER_DEVICES_STEP)

        failAfterFirstUser = false
        migration(flaky, userDeviceRepository = failingRepository).run(null)

        assertThat(state.completed).contains(PostgresToCouchDbMigration.USER_DEVICES_STEP)
        assertThat(devices.findByUserIdentifier("user-1")).hasSize(1)
        assertThat(devices.findByUserIdentifier("user-2")).hasSize(1)
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

        assertThat(devices.byUser).isEmpty()
        assertThat(sessions.stored).isEmpty()
    }

    @Test
    fun `does nothing when no datasource is configured`() {
        migration(null).run(null)

        assertThat(devices.byUser).isEmpty()
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

        assertThat(devices.byUser).isEmpty()
        assertThat(state.completed).isEmpty()
    }
}
