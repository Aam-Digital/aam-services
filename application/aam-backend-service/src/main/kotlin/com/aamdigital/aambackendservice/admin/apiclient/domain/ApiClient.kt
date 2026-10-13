package com.aamdigital.aambackendservice.admin.apiclient.domain

/**
 * A Keycloak client that an external system uses to access the API with the client credentials flow,
 * created and managed through this module.
 *
 * @property clientId the id the external system authenticates with
 * @property name label given by the user who created it, e.g. the name of the external system
 * @property scopes the client scopes the client has as Default scopes, i.e. the API access it has
 * @property createdBy the user who created it
 * @property createdAt ISO-8601 timestamp of the creation
 */
data class ApiClient(
    val clientId: String,
    val name: String,
    val scopes: Set<String>,
    val createdBy: String?,
    val createdAt: String?
)

/**
 * What an external system needs to request access tokens for an [ApiClient].
 */
data class ApiClientCredentials(
    val clientId: String,
    val clientSecret: String
)
