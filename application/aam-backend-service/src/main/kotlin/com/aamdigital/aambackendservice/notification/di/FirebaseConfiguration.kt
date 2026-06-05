package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationFirebaseMode
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@ConfigurationProperties("notification-firebase-configuration")
@ConditionalOnNotificationApiEnabled
@ConditionalOnNotificationFirebaseMode
class NotificationFirebaseClientConfiguration(
    val credentialFileBase64: String,
    val linkBaseUrl: String
)

@Configuration
@ConditionalOnNotificationApiEnabled
class FirebaseConfiguration {
    @Bean
    @ConditionalOnNotificationFirebaseMode
    fun firebaseApp(notificationFirebaseClientConfiguration: NotificationFirebaseClientConfiguration): FirebaseApp {
        val credentialFile =
            Base64.getDecoder().decode(notificationFirebaseClientConfiguration.credentialFileBase64)

        val options =
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(credentialFile.inputStream()))
                .build()

        return FirebaseApp.initializeApp(options)
    }

    @Bean
    @ConditionalOnNotificationFirebaseMode
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging = FirebaseMessaging.getInstance(firebaseApp)
}
