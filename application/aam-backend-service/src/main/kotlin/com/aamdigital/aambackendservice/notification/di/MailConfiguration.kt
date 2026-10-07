package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.mail.SmtpMailSenderService
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailConfigured
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
@ConditionalOnNotificationEmailConfigured
class MailConfiguration {
    @Bean
    fun mailSenderService(javaMailSender: JavaMailSender): MailSenderService = SmtpMailSenderService(javaMailSender)
}
