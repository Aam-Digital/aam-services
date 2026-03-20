package com.aamdigital.aambackendservice.common.couchdb.core

import org.springframework.util.LinkedMultiValueMap

/** Returns query params for `_all_docs` scoped to documents with the given key prefix. */
fun getQueryParamsAllDocs(key: String): LinkedMultiValueMap<String, String> {
    val queryParams = LinkedMultiValueMap<String, String>()
    queryParams.add("include_docs", "true")
    queryParams.add("startkey", "\"$key:\"")
    queryParams.add("endkey", "\"$key:\\ufff0\"")
    return queryParams
}

/** Creates an empty [LinkedMultiValueMap] for CouchDB query parameters. */
fun getEmptyQueryParams() = LinkedMultiValueMap<String, String>()
