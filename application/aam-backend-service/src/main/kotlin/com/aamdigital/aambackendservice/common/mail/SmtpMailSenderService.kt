package com.aamdigital.aambackendservice.common.mail

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

class SmtpMailSenderService(
    private val javaMailSender: JavaMailSender
) : MailSenderService {
    override fun sendMail(request: MailSenderRequest): MailSenderResponse {
        val mimeMessage = javaMailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, "UTF-8")
        helper.setTo(request.to)
        helper.setSubject(request.subject)
        helper.setText(request.body, request.isHtml)
        request.headers.forEach { (key, value) -> mimeMessage.addHeader(key, value) }
        javaMailSender.send(mimeMessage)
        return MailSenderResponse(success = true)
    }
}
