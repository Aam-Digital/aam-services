package com.aamdigital.aambackendservice.common.changes

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SharedSyncEntryMigrationTest {
    private val repository = InMemorySyncRepository()

    private fun migration(consumers: List<String> = listOf("reporting", "notification")) =
        SharedSyncEntryMigration(repository, databases = listOf("app"), consumerNames = consumers)

    private fun cursorOf(consumer: String?) = repository.findByDatabase("app", consumer).orElse(null)

    @Test
    fun `should continue every consumer from the shared cursor`() {
        // Given
        repository.save(SyncEntry(database = "app", latestRef = "seq-shared"))

        // When
        migration().migrateIfPending()

        // Then
        assertThat(cursorOf("reporting")).isEqualTo(SyncEntry("app", "seq-shared", "reporting"))
        assertThat(cursorOf("notification")).isEqualTo(SyncEntry("app", "seq-shared", "notification"))
    }

    @Test
    fun `should delete the shared cursor, so a module enabled later starts from now instead of replaying`() {
        repository.save(SyncEntry(database = "app", latestRef = "seq-shared"))

        migration(consumers = listOf("reporting")).migrateIfPending()

        assertThat(cursorOf(null)).isNull()
        assertThat(cursorOf("notification")).isNull()
    }

    @Test
    fun `should leave a consumer's own cursor alone`() {
        repository.save(SyncEntry(database = "app", latestRef = "seq-shared"))
        repository.save(SyncEntry(database = "app", latestRef = "seq-newer", consumer = "reporting"))

        migration().migrateIfPending()

        assertThat(cursorOf("reporting")?.latestRef).isEqualTo("seq-newer")
        assertThat(cursorOf("notification")?.latestRef).isEqualTo("seq-shared")
        assertThat(cursorOf(null)).isNull()
    }

    @Test
    fun `should do nothing without a shared cursor`() {
        migration().migrateIfPending()

        assertThat(repository.entries).isEmpty()
    }

    @Test
    fun `should try again after a failure instead of letting consumers start from now`() {
        // Given
        repository.save(SyncEntry(database = "app", latestRef = "seq-shared"))
        var failing = true
        val flaky =
            object : SyncRepository by repository {
                override fun save(syncEntry: SyncEntry): SyncEntry {
                    check(!failing) { "couchdb unavailable" }
                    return repository.save(syncEntry)
                }
            }
        val migration = SharedSyncEntryMigration(flaky, listOf("app"), listOf("reporting", "notification"))

        // When
        assertThatThrownBy { migration.migrateIfPending() }.isInstanceOf(IllegalStateException::class.java)
        failing = false
        migration.migrateIfPending()

        // Then
        assertThat(cursorOf("reporting")?.latestRef).isEqualTo("seq-shared")
        assertThat(cursorOf("notification")?.latestRef).isEqualTo("seq-shared")
        assertThat(cursorOf(null)).isNull()
    }

    @Test
    fun `should not read the shared cursor again once it has succeeded`() {
        // Given
        var reads = 0
        val counting =
            object : SyncRepository by repository {
                override fun findByDatabase(
                    database: String,
                    consumer: String?
                ) = repository.findByDatabase(database, consumer).also { reads++ }
            }
        val migration = SharedSyncEntryMigration(counting, listOf("app"), listOf("reporting"))

        // When
        migration.migrateIfPending()
        migration.migrateIfPending()

        // Then
        assertThat(reads).isEqualTo(1)
    }
}
