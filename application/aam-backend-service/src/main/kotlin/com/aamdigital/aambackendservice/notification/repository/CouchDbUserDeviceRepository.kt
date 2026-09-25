package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime
import java.util.*

/**
 * [UserDeviceRepository] backed by one CouchDB document per device, keyed by the device token -
 * the same shape as the PostgreSQL table it replaces.
 */
class CouchDbUserDeviceRepository(
    private val couchDbClient: CouchDbClient
) : UserDeviceRepository {
    companion object {
        const val DOCUMENT_PREFIX = "UserDevice"
    }

    override fun findByUserIdentifier(
        userIdentifier: String,
        pageable: Pageable
    ): Page<UserDeviceEntity> {
        val devices =
            couchDbClient.findDatabaseDocumentsByPrefix(
                database = BACKEND_STATE_DATABASE,
                prefix = DOCUMENT_PREFIX,
                selector = mapOf("userIdentifier" to userIdentifier),
                kClass = UserDeviceEntity::class
            )

        if (pageable.isUnpaged) return PageImpl(devices)

        val page = devices.drop(pageable.offset.toInt()).take(pageable.pageSize)
        return PageImpl(page, pageable, devices.size.toLong())
    }

    override fun findByDeviceToken(deviceToken: String): Optional<UserDeviceEntity> =
        try {
            Optional.of(
                couchDbClient.getDatabaseDocument(
                    database = BACKEND_STATE_DATABASE,
                    documentId = documentId(deviceToken),
                    kClass = UserDeviceEntity::class
                )
            )
        } catch (_: NotFoundException) {
            Optional.empty()
        }

    override fun existsByDeviceToken(deviceToken: String): Boolean =
        couchDbClient
            .headDatabaseDocument(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId(deviceToken)
            ).eTag != null

    /**
     * A device deleted concurrently (by a second tab, say) is gone either way: CouchDB then answers
     * 404, or 409 for the delete whose revision was deleted first. A 409 can also mean the token was
     * registered again in between, so it only counts as deleted once the document is really gone.
     */
    override fun deleteByDeviceToken(deviceToken: String) {
        try {
            couchDbClient.deleteDatabaseDocument(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId(deviceToken)
            )
        } catch (ex: ExternalSystemException) {
            when (ex.code) {
                DefaultCouchDbClientErrorCode.NOT_FOUND -> Unit
                DefaultCouchDbClientErrorCode.CONFLICT -> if (existsByDeviceToken(deviceToken)) throw ex
                else -> throw ex
            }
        }
    }

    /**
     * Written only if no document exists for the token yet, which keeps the token unique across
     * users like the unique column of the PostgreSQL table did.
     */
    override fun save(userDevice: UserDeviceEntity) {
        try {
            couchDbClient.putDatabaseDocumentAtRevision(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId(userDevice.deviceToken),
                body = userDevice.copy(createdAt = userDevice.createdAt ?: OffsetDateTime.now()),
                expectedRev = null
            )
        } catch (ex: ExternalSystemException) {
            if (ex.code == DefaultCouchDbClientErrorCode.CONFLICT) throw DeviceAlreadyRegisteredException(ex)
            throw ex
        }
    }

    private fun documentId(deviceToken: String) = "$DOCUMENT_PREFIX:$deviceToken"
}
