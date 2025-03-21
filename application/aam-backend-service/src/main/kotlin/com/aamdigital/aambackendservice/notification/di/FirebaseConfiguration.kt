package com.aamdigital.aambackendservice.notification.di

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@ConfigurationProperties("notification-firebase-configuration")
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NotificationFirebaseClientConfiguration(
    val credentialFileBase64: String,
    val linkBaseUrl: String,
)

@Configuration
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class FirebaseConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "features.notification-api",
        name = ["mode"],
        havingValue = "firebase",
        matchIfMissing = false
    )
    fun firebaseApp(notificationFirebaseClientConfiguration: NotificationFirebaseClientConfiguration): FirebaseApp {
        val credentialFile =
            Base64.getDecoder().decode(notificationFirebaseClientConfiguration.credentialFileBase64)

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(credentialFile.inputStream()))
            .build()

        return FirebaseApp.initializeApp(options)
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "features.notification-api",
        name = ["mode"],
        havingValue = "firebase",
        matchIfMissing = false
    )
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging {
        return FirebaseMessaging.getInstance(firebaseApp)
    }
}
