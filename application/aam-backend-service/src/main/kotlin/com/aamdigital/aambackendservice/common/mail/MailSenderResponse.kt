package com.aamdigital.aambackendservice.common.mail

data class MailSenderResponse(
    val success: Boolean,
    val messageReference: String? = null
)
