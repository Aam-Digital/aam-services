package com.aamdigital.aambackendservice.common.mail

interface MailSenderService {
    fun sendMail(request: MailSenderRequest): MailSenderResponse
}
