package com.aamdigital.aambackendservice.common.couchdb.core

import org.springframework.util.LinkedMultiValueMap

fun getQueryParamsAllDocs(key: String): LinkedMultiValueMap<String, String> {
    val queryParams = LinkedMultiValueMap<String, String>()
    queryParams.add("include_docs", "true")
    queryParams.add("startkey", "\"$key:\"")
    queryParams.add("endkey", "\"$key:\\ufff0\"")
    return queryParams
}

fun getEmptyQueryParams() = LinkedMultiValueMap<String, String>()
