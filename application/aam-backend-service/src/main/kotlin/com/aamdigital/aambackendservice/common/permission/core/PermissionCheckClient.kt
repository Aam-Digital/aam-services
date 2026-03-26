package com.aamdigital.aambackendservice.common.permission.core

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Client for checking entity-level permissions via the replication-backend API.
 *
 * When no [RestClient] is configured (e.g. missing environment config),
 * all permission checks permit by default (allow-all).
 */
class PermissionCheckClient(
    private val replicationBackendClient: RestClient? = null
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun checkPermissions(
        userIds: List<String>,
        entityId: String,
        action: String = "read"
    ): Map<String, Boolean> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        if (replicationBackendClient == null) {
            logger.warn("PermissionCheckClient is not configured; allowing all permissions by default")
            return userIds.associateWith { true }
        }

        val request = PermissionCheckRequest(
            userIds = userIds,
            entityId = entityId,
            action = action
        )
        logger.debug(
            "Sending permission check: userIds={}, entityId={}, action={}",
            request.userIds,
            request.entityId,
            request.action
        )

        return try {
            val responseBody =
                replicationBackendClient
                    .post()
                    .uri("/permissions/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map::class.java)
                    ?: emptyMap<String, Any>()

            responseBody.entries
                .filter { it.key is String }
                .associate { entry ->
                    val resultMap = entry.value as? Map<*, *>
                    val permissionValue = resultMap?.get("permitted") as? Boolean
                    val errorCode = resultMap?.get("error") as? String

                    if (errorCode != null) {
                        logger.warn(
                            "Permission check for user {} returned error: {}",
                            entry.key,
                            errorCode
                        )
                    } else if (permissionValue == null) {
                        logger.warn("Unexpected permission check response payload for entry {}", entry.value)
                    }

                    (entry.key as String) to (permissionValue ?: false)
                }
        } catch (exception: Exception) {
            logger.warn("Permission check request failed; denying notifications by default", exception)
            emptyMap()
        }
    }
}

/**
 * Request payload sent to the replication-backend `/permissions/check` endpoint.
 */
data class PermissionCheckRequest(
    val userIds: List<String>,
    val entityId: String,
    val action: String
)
