package com.aamdigital.aambackendservice.e2e

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Generates a throwaway RSA-2048 service-account credential at runtime and injects it as
 * notification-firebase-configuration.credential-file-base64 so firebase mode can boot without
 * a committed private key. The credential is structurally valid (GoogleCredentials parses it
 * eagerly) but references a nonexistent project and never reaches FCM in the no-registered-
 * devices test path.
 */
class FirebaseTestCredentialInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = buildString {
            append("-----BEGIN PRIVATE KEY-----\n")
            append(Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(keyPair.private.encoded))
            append("\n-----END PRIVATE KEY-----\n")
        }
        val pemJson = pem.replace("\n", "\\n")

        val serviceAccountJson =
            """{"type":"service_account","project_id":"aam-e2e-test","private_key_id":"e2e-throwaway","private_key":"$pemJson","client_email":"e2e@aam-e2e-test.iam.gserviceaccount.com","client_id":"000000000000000000000","auth_uri":"https://accounts.google.com/o/oauth2/auth","token_uri":"https://oauth2.googleapis.com/token"}"""

        val encoded = Base64.getEncoder().encodeToString(serviceAccountJson.toByteArray())

        applicationContext.environment.propertySources.addFirst(
            MapPropertySource(
                "firebase-e2e-credential",
                mapOf("notification-firebase-configuration.credential-file-base64" to encoded)
            )
        )
    }
}
