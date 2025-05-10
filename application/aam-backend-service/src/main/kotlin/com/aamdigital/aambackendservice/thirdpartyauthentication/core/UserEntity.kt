package com.aamdigital.aambackendservice.thirdpartyauthentication.core

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Simple (CouchDB) User Entity model to create a user record.
 */
data class UserEntity(
    @JsonProperty("_id")
    val id: String,

    val name: String,
)
