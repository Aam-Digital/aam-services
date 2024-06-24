package com.aamdigital.aambackendservice.domain

data class EntityAttribute(
    val name: String,
    val type: EntityAttributeType,
)

data class EntityAttributeType(
    val field: String,
    val type: String,
)

data class EntityType(
    val label: String,
    val attributes: List<EntityAttribute>,
)

data class EntityConfig(
    val version: String,
    val entities: List<EntityType>,
)
