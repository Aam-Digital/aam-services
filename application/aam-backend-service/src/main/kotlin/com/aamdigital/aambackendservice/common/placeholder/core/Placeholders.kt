package com.aamdigital.aambackendservice.common.placeholder.core

/**
 * Placeholders that configuration documents can use in place of a value that differs per user,
 * e.g. `{"assignedTo": {"$elemMatch": "$current_user"}}`.
 *
 * The names are shared with the frontend (`PLACEHOLDERS` in ndb-core), so a configuration document
 * means the same on both sides. A placeholder is always a complete string value, never part of one.
 */
object Placeholders {
    /** The id of the entity linked to the user account (e.g. `User:alice`). */
    const val CURRENT_USER = "\$current_user"

    /** All placeholders the backend knows, see [PlaceholderResolver]. */
    val ALL: Set<String> = setOf(CURRENT_USER)
}
