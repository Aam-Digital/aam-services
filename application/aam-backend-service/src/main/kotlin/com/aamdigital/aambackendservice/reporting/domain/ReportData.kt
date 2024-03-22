package com.aamdigital.aambackendservice.reporting.domain

import com.aamdigital.aambackendservice.domain.DomainReference
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

data class ReportData(
    @JsonProperty("_id")
    val id: String,
    val report: DomainReference,
    val calculation: DomainReference,
    var data: List<*>,
) {
    @OptIn(ExperimentalStdlibApi::class)
    fun getDataHash(): String {
        val mapper = ObjectMapper()
        val md = MessageDigest.getInstance("SHA-256")
        val input = mapper.writeValueAsString(data).toByteArray()
        val bytes = md.digest(input)
        return bytes.toHexString()
    }
}
