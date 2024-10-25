package com.aamdigital.aambackendservice.events

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Representation of an DomainEvent
 *
 * @param id uniq, incrementing number
 * @param createdAt ISO_DATE_TIME string, created when even is initialised
 */
abstract class DomainEvent(
    val id: Long = SequenceGenerator.nextId(),
    val createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).toString(),
)
