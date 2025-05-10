package com.aamdigital.aambackendservice.reporting.transformation

/**
 * Represents a data transformation
 * Will apply implemented logic to input data and returns transformed data as output
 *
 * e.g. UpperCaseTransformation, ReportStartDateTransformation, ...
 *
 */
interface DataTransformation<T> {
    val id: String

    fun transform(data: T): T
}
