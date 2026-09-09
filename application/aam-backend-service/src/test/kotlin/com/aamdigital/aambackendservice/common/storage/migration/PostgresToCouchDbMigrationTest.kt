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

    private fun migration(source: LegacyPostgresSource?) =
        PostgresToCouchDbMigration(
            legacyPostgresSource = { source },
            userDeviceRepository = Provider(devices),
            thirdPartyAuthSessionRepository = Provider(sessions)
        )

    @BeforeEach
    fun setUp() {
        devices = FakeUserDeviceRepository()
        sessions = FakeThirdPartyAuthSessionRepository()
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
        assertThat(devices.byUser.getValue("user-1").first().deviceName).isEqualTo("Phone")
        assertThat(devices.byUser.getValue("user-1").first().createdAt).isEqualTo(createdAt)
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
    }
}
