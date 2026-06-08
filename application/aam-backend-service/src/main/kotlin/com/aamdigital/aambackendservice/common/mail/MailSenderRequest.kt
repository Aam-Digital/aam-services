package com.aamdigital.aambackendservice.common.mail

data class MailSenderRequest(
    val to: String,
    val subject: String,
    val body: String,
    val from: String = "",
    val isHtml: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)
