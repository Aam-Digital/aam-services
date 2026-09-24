package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import java.time.OffsetDateTime

class CouchDbUserDeviceRepositoryTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val repository = CouchDbUserDeviceRepository(couchDbClient)

    private val phone = UserDeviceEntity(deviceName = "Phone", deviceToken = "token-phone", userIdentifier = "user-1")
    private val tablet = phone.copy(deviceName = "Tablet", deviceToken = "token-tablet")

    private fun stubFind(vararg devices: UserDeviceEntity) {
        whenever(
            couchDbClient.findDatabaseDocuments(
                eq(BACKEND_STATE_DATABASE),
                any(),
                any(),
                eq(UserDeviceEntity::class)
            )
        ).thenReturn(FindResponse(docs = devices.toList()))
    }

    /** A token registered by another user must not be taken over, as the unique column prevented before. */
    @Test
    fun `creates the device document only if it does not exist yet`() {
        repository.save(phone)

        verify(couchDbClient).putDatabaseDocumentAtRevision(
            eq(BACKEND_STATE_DATABASE),
            eq("UserDevice:token-phone"),
            argThat<UserDeviceEntity> { userIdentifier == "user-1" && createdAt != null },
            isNull()
        )
    }

    @Test
    fun `keeps a given creation time`() {
        val createdAt = OffsetDateTime.parse("2024-01-01T00:00:00Z")

        repository.save(phone.copy(createdAt = createdAt))

        verify(couchDbClient).putDatabaseDocumentAtRevision(
            any(),
            any(),
            argThat<UserDeviceEntity> { this.createdAt == createdAt },
            isNull()
        )
    }

    @Test
    fun `queries the devices of a user among the device documents only`() {
        stubFind(phone, tablet)

        val result = repository.findByUserIdentifier("user-1", Pageable.unpaged())

        assertThat(result.content).containsExactly(phone, tablet)
        verify(couchDbClient).findDatabaseDocuments(
            eq(BACKEND_STATE_DATABASE),
            argThat<Map<String, Any>> {
                this["selector"] ==
                    mapOf(
                        "_id" to mapOf("\$gt" to "UserDevice:", "\$lt" to "UserDevice:￰"),
                        "userIdentifier" to "user-1"
                    ) &&
                    this["limit"] != null
            },
            any(),
            eq(UserDeviceEntity::class)
        )
    }

    @Test
    fun `pages the devices of a user`() {
        stubFind(phone, tablet)

        val result = repository.findByUserIdentifier("user-1", PageRequest.of(1, 1))

        assertThat(result.content).containsExactly(tablet)
        assertThat(result.totalElements).isEqualTo(2)
    }

    @Test
    fun `finds nothing for an unknown token`() {
        whenever(
            couchDbClient.getDatabaseDocument(
                eq(BACKEND_STATE_DATABASE),
                eq("UserDevice:unknown"),
                any(),
                eq(UserDeviceEntity::class)
            )
        ).thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))

        assertThat(repository.findByDeviceToken("unknown")).isEmpty()
    }

    @Test
    fun `a device exists when its document has a revision`() {
        whenever(couchDbClient.headDatabaseDocument(BACKEND_STATE_DATABASE, "UserDevice:token-phone"))
            .thenReturn(HttpHeaders().apply { eTag = "\"1-a\"" })
        whenever(couchDbClient.headDatabaseDocument(BACKEND_STATE_DATABASE, "UserDevice:unknown"))
            .thenReturn(HttpHeaders())

        assertThat(repository.existsByDeviceToken("token-phone")).isTrue()
        assertThat(repository.existsByDeviceToken("unknown")).isFalse()
    }

    /** Two tabs registering the same token at once: the second must be told, not get a 500. */
    @Test
    fun `reports a token that is already registered`() {
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), isNull()))
            .thenThrow(ExternalSystemException(code = DefaultCouchDbClientErrorCode.CONFLICT))

        assertThatThrownBy { repository.save(phone) }.isInstanceOf(DeviceAlreadyRegisteredException::class.java)
    }

    @Test
    fun `passes on other failures to register`() {
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), isNull()))
            .thenThrow(ExternalSystemException(code = DefaultCouchDbClientErrorCode.OTHER_COUCHDB_ERROR))

        assertThatThrownBy { repository.save(phone) }.isInstanceOf(ExternalSystemException::class.java)
    }

    private fun stubDeleteFailure(code: DefaultCouchDbClientErrorCode) {
        whenever(couchDbClient.deleteDatabaseDocument(BACKEND_STATE_DATABASE, "UserDevice:token-phone"))
            .thenAnswer { throw ExternalSystemException(code = code) }
    }

    /** Two concurrent unregistrations: whichever comes second finds nothing left to delete. */
    @Test
    fun `deleting a device that is already gone does nothing`() {
        stubDeleteFailure(DefaultCouchDbClientErrorCode.NOT_FOUND)

        assertThatCode { repository.deleteByDeviceToken("token-phone") }.doesNotThrowAnyException()
    }

    /** CouchDB answers 409 to a delete whose revision a concurrent delete removed first. */
    @Test
    fun `deleting a device whose revision is already gone does nothing`() {
        stubDeleteFailure(DefaultCouchDbClientErrorCode.CONFLICT)
        whenever(couchDbClient.headDatabaseDocument(BACKEND_STATE_DATABASE, "UserDevice:token-phone"))
            .thenReturn(HttpHeaders())

        assertThatCode { repository.deleteByDeviceToken("token-phone") }.doesNotThrowAnyException()
    }

    /** The token was registered again between looking up the revision and deleting it. */
    @Test
    fun `does not report a device as deleted that still exists after a conflict`() {
        stubDeleteFailure(DefaultCouchDbClientErrorCode.CONFLICT)
        whenever(couchDbClient.headDatabaseDocument(BACKEND_STATE_DATABASE, "UserDevice:token-phone"))
            .thenReturn(HttpHeaders().apply { eTag = "\"3-c\"" })

        assertThatThrownBy { repository.deleteByDeviceToken("token-phone") }
            .isInstanceOf(ExternalSystemException::class.java)
    }

    @Test
    fun `passes on other failures to delete`() {
        stubDeleteFailure(DefaultCouchDbClientErrorCode.OTHER_COUCHDB_ERROR)

        assertThatThrownBy { repository.deleteByDeviceToken("token-phone") }
            .isInstanceOf(ExternalSystemException::class.java)
    }
}
