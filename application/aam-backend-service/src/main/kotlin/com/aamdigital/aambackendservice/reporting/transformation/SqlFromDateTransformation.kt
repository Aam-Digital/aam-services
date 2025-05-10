package com.aamdigital.aambackendservice.reporting.transformation

/**
 * Will cut the 'from' date always to '2022-04-25'
 * If data is empty, will return the DEFAULT_FROM_DATE
 */
class SqlFromDateTransformation : DataTransformation<String> {
    companion object {
        const val DEFAULT_FROM_DATE = "0000-01-01"
        const val INDEX_ISO_STRING_DATE_END = 10
    }

    override val id: String
        get() = "SQL_FROM_DATE"

    override fun transform(data: String): String {
        if (data.isEmpty()) {
            return DEFAULT_FROM_DATE
        }
        return data.substring(0, INDEX_ISO_STRING_DATE_END)
    }
}
