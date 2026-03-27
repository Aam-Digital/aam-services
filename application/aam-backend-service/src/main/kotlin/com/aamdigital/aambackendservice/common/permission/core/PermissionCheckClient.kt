package com.aamdigital.aambackendservice.common.permission.core

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Client for checking entity-level permissions via the replication-backend API.
 *
 * When no [RestClient] is provided (no replication-backend configured),
 * all permission checks permit by default (allow-all).
 * This is expected when the system runs without access control
 * (i.e. no Config:Permissions doc / all users have "manage all").
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
            logger.debug("PermissionCheckClient is not configured; allowing all permissions by default")
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
                    .body(object : ParameterizedTypeReference<Map<String, PermissionCheckResult>>() {})
                    ?: emptyMap()

            responseBody.entries
                .associate { entry ->
                    val permissionValue = entry.value.permitted
                    val errorCode = entry.value.error

                    if (errorCode != null) {
                        logger.warn(
                            "Permission check for user {} returned error: {}",
                            entry.key,
                            errorCode
                        )
                    } else if (permissionValue == null) {
                        logger.warn("Unexpected permission check response payload for user {}", entry.key)
                    }

                    entry.key to (permissionValue ?: false)
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

data class PermissionCheckResult(
    val permitted: Boolean? = null,
    val error: String? = null
)
