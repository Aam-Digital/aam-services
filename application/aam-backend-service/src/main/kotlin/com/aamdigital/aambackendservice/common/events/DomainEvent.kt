package com.aamdigital.aambackendservice.common.events

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Base class of a DomainEvent. An event can be triggered in the application or by external services.
 * Events are normally consumed and written to a message broker queue and are processed asynchronously.
 * Each event has a unique, ascending identifier. This enables event duplication at message broker level.
 *
 * e.g. ReportCalculationEvent, DatabaseChangeEvent
 *
 * @param id uniq, incrementing number
 * @param createdAt ISO_DATE_TIME string, created when even is initialised
 */
abstract class DomainEvent(
    val id: Long = SequenceGenerator.nextId(),
    val createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).toString(),
)
