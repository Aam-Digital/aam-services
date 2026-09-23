package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.domain.UserDevice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CouchDbUserDeviceRepositoryTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val repository = CouchDbUserDeviceRepository(couchDbClient)

    private val documentId = "UserDevice:user-1"
    private val phone = UserDevice(deviceToken = "token-phone", deviceName = "Phone")
    private val tablet = UserDevice(deviceToken = "token-tablet", deviceName = "Tablet")

    private fun stubReads(vararg results: StoredUserDeviceRegistrations?) {
        var stubbing =
            whenever(
                couchDbClient.getDatabaseDocument(
                    eq(BACKEND_STATE_DATABASE),
                    eq(documentId),
                    any(),
                    eq(StoredUserDeviceRegistrations::class)
                )
            )
        results.forEach { result ->
            stubbing =
                if (result == null) {
                    stubbing.thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))
                } else {
                    stubbing.thenReturn(result)
                }
        }
    }

    private fun conflict() = ExternalSystemException(message = "409 conflict", code = TestErrorCode.TEST_EXCEPTION)

    @Test
    fun `writes against the revision it read`() {
        stubReads(StoredUserDeviceRegistrations(rev = "1-a", devices = listOf(phone)))

        repository.addDevice("user-1", tablet)

        verify(couchDbClient).putDatabaseDocumentAtRevision(
            eq(BACKEND_STATE_DATABASE),
            eq(documentId),
            eq(UserDeviceRegistrations(userIdentifier = "user-1", devices = listOf(phone, tablet))),
            eq("1-a")
        )
    }

    @Test
    fun `creates the document only if it does not exist yet`() {
        stubReads(null)

        repository.addDevice("user-1", phone)

        verify(couchDbClient).putDatabaseDocumentAtRevision(
            eq(BACKEND_STATE_DATABASE),
            eq(documentId),
            eq(UserDeviceRegistrations(userIdentifier = "user-1", devices = listOf(phone))),
            isNull()
        )
    }

    /** Two registrations racing: the loser must re-read and keep the winner's device. */
    @Test
    fun `retries on a conflict on top of the concurrent write`() {
        stubReads(
            StoredUserDeviceRegistrations(rev = "1-a", devices = emptyList()),
            StoredUserDeviceRegistrations(rev = "2-b", devices = listOf(phone))
        )
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), eq("1-a")))
            .thenThrow(conflict())
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), eq("2-b")))
            .thenReturn(DocSuccess(ok = true, id = documentId, rev = "3-c"))

        repository.addDevice("user-1", tablet)

        verify(couchDbClient).putDatabaseDocumentAtRevision(
            eq(BACKEND_STATE_DATABASE),
            eq(documentId),
            argThat<UserDeviceRegistrations> { devices == listOf(phone, tablet) },
            eq("2-b")
        )
    }

    @Test
    fun `gives up after repeated conflicts`() {
        stubReads(StoredUserDeviceRegistrations(rev = "1-a", devices = emptyList()))
        whenever(couchDbClient.putDatabaseDocumentAtRevision(any(), any(), any(), any()))
            .thenThrow(conflict())

        assertThrows<ExternalSystemException> { repository.removeDevice("user-1", "token-phone") }

        verify(couchDbClient, times(3)).putDatabaseDocumentAtRevision(any(), any(), any(), any())
    }

    @Test
    fun `reads the devices of a user`() {
        stubReads(StoredUserDeviceRegistrations(rev = "1-a", devices = listOf(phone, tablet)))

        assertThat(repository.findDevice("user-1", "token-tablet")).isEqualTo(tablet)
    }
}
