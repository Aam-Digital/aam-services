package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.mail.SmtpMailSenderService
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailEnabled
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
@ConditionalOnNotificationEmailEnabled
class MailConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = ["host"])
    fun mailSenderService(javaMailSender: JavaMailSender): MailSenderService = SmtpMailSenderService(javaMailSender)
}
