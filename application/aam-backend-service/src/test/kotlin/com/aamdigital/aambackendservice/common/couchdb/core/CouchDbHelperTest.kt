package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CouchDbHelperTest {
    private val couchDbClient = mock<CouchDbClient>()

    private val missingDatabase = ExternalSystemException(code = DefaultCouchDbClientErrorCode.DATABASE_NOT_FOUND)

    /** A write that fails with [failures] in turn, then succeeds, counting how often it ran. */
    private class Write(
        vararg failures: Exception
    ) : () -> String {
        private val remainingFailures = ArrayDeque(failures.toList())
        var calls = 0

        override fun invoke(): String {
            calls++
            remainingFailures.removeFirstOrNull()?.let { throw it }
            return "written"
        }
    }

    @Test
    fun `runs the write once without creating anything when it succeeds`() {
        // Given
        val write = Write()

        // When
        val result = couchDbClient.creatingDatabaseIfMissing("db", write)

        // Then
        assertThat(result).isEqualTo("written")
        assertThat(write.calls).isEqualTo(1)
        verify(couchDbClient, never()).createDatabase(any())
    }

    @Test
    fun `creates the missing database and runs the write again`() {
        // Given
        val write = Write(missingDatabase)

        // When
        val result = couchDbClient.creatingDatabaseIfMissing("db", write)

        // Then
        assertThat(result).isEqualTo("written")
        assertThat(write.calls).isEqualTo(2)
        verify(couchDbClient).createDatabase("db")
    }

    @ParameterizedTest
    @EnumSource(DefaultCouchDbClientErrorCode::class, names = ["DATABASE_NOT_FOUND"], mode = EnumSource.Mode.EXCLUDE)
    fun `does not create a database for a missing document or any other failure`(code: DefaultCouchDbClientErrorCode) {
        // Given
        val failure = ExternalSystemException(code = code)
        val write = Write(failure)

        // When
        val thrown = catchThrowable { couchDbClient.creatingDatabaseIfMissing("db", write) }

        // Then
        assertThat(thrown).isSameAs(failure)
        assertThat(write.calls).isEqualTo(1)
        verify(couchDbClient, never()).createDatabase(any())
    }

    @Test
    fun `gives up when the database is still missing after creating it`() {
        // Given it was dropped again right away, say
        val write = Write(missingDatabase, missingDatabase)

        // When
        val thrown = catchThrowable { couchDbClient.creatingDatabaseIfMissing("db", write) }

        // Then
        assertThat(thrown).isSameAs(missingDatabase)
        assertThat(write.calls).isEqualTo(2)
        verify(couchDbClient, times(1)).createDatabase("db")
    }

    @Test
    fun `does not write again when the database could not be created`() {
        // Given
        val refused = ExternalSystemException(code = DefaultCouchDbClientErrorCode.OTHER_COUCHDB_ERROR)
        doAnswer { throw refused }.whenever(couchDbClient).createDatabase("db")
        val write = Write(missingDatabase)

        // When
        val thrown = catchThrowable { couchDbClient.creatingDatabaseIfMissing("db", write) }

        // Then
        assertThat(thrown).isSameAs(refused)
        assertThat(write.calls).isEqualTo(1)
    }
}
