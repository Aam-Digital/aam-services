package com.aamdigital.aambackendservice.domain

/**
 * Metadata for a CouchDB entity to show who and when it was updated.
 * --> frontend `UpdateMetadata` class
 */
data class UpdateMetadata(
    /** Timestamp **/
    val at: String,

    /** User ID **/
    val by: String,
)
