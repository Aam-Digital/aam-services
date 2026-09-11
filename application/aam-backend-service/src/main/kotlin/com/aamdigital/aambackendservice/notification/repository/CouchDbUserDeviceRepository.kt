package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.error.AamException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.domain.UserDevice
import org.slf4j.LoggerFactory

/**
 * The registrations of a single user, stored as one CouchDB document.
 *
 * One document per user rather than per device: push delivery reads by user, which makes that the
 * hot path a plain document read, and both REST endpoints know the caller from their JWT.
 */
data class UserDeviceRegistrations(
    val userIdentifier: String,
    val devices: List<UserDevice> = emptyList()
)

class CouchDbUserDeviceRepository(
    private val couchDbClient: CouchDbClient
) : UserDeviceRepository {
    companion object {
        const val DOCUMENT_PREFIX = "UserDevice"

        /**
         * A user registering two devices at the same moment can lose one: the client reads, then
         * writes with the revision it read. Retrying re-reads the document the winner wrote.
         */
        private const val WRITE_ATTEMPTS = 3
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun findByUserIdentifier(userIdentifier: String): List<UserDevice> =
        fetch(userIdentifier)?.devices.orEmpty()

    override fun findDevice(
        userIdentifier: String,
        deviceToken: String
    ): UserDevice? = findByUserIdentifier(userIdentifier).firstOrNull { it.deviceToken == deviceToken }

    override fun addDevice(
        userIdentifier: String,
        device: UserDevice
    ) = update(userIdentifier) { devices ->
        devices.filterNot { it.deviceToken == device.deviceToken } + device
    }

    override fun removeDevice(
        userIdentifier: String,
        deviceToken: String
    ) = update(userIdentifier) { devices ->
        devices.filterNot { it.deviceToken == deviceToken }
    }

    private fun update(
        userIdentifier: String,
        change: (List<UserDevice>) -> List<UserDevice>
    ) {
        var lastError: AamException? = null

        repeat(WRITE_ATTEMPTS) { attempt ->
            val current = fetch(userIdentifier)?.devices.orEmpty()

            try {
                couchDbClient.putDatabaseDocument(
                    database = BACKEND_STATE_DATABASE,
                    documentId = documentId(userIdentifier),
                    body =
                        UserDeviceRegistrations(
                            userIdentifier = userIdentifier,
                            devices = change(current)
                        )
                )
                return
            } catch (ex: AamException) {
                lastError = ex
                logger.debug("[CouchDbUserDeviceRepository] write attempt {} failed", attempt + 1, ex)
            }
        }

        throw lastError ?: IllegalStateException("Could not store devices for user $userIdentifier")
    }

    private fun fetch(userIdentifier: String): UserDeviceRegistrations? =
        try {
            couchDbClient.getDatabaseDocument(
                database = BACKEND_STATE_DATABASE,
                documentId = documentId(userIdentifier),
                kClass = UserDeviceRegistrations::class
            )
        } catch (_: NotFoundException) {
            null
        }

    private fun documentId(userIdentifier: String) = "$DOCUMENT_PREFIX:$userIdentifier"
}
