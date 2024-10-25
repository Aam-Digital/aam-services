package com.aamdigital.aambackendservice.reporting.transformation

import com.aamdigital.aambackendservice.reporting.domain.DataTransformation

/**
 * Will set 'to' date to DEFAULT_TO_DATE if empty
 * Will set 'to' date to last minute of the day
 */
class SqlToDateTransformation : DataTransformation<String> {
    companion object {
        const val DEFAULT_TO_DATE = "9999-12-31T23:59:59.999Z"
        const val INDEX_ISO_STRING_DATE_END = 10
    }

    override val id: String
        get() = "SQL_TO_DATE"

    override fun transform(data: String): String {
        if (data.isEmpty()) {
            return DEFAULT_TO_DATE
        }
        return data.substring(0, INDEX_ISO_STRING_DATE_END) + "T23:59:59.999Z"
    }
}
