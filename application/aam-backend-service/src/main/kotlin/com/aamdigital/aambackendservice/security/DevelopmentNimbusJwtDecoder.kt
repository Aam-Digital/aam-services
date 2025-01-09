package com.aamdigital.aambackendservice.security


import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestTemplate
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


/**
 * DO NOT USE IN PRODUCTION
 */
@Configuration
class DevelopmentNimbusJwtDecoder {

    private val insecureTrustManager = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }

            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }
    )

    @Bean
    @Profile("local-docker-development")
    fun sslCheckDisabledJwtDecoder(): JwtDecoder {
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, insecureTrustManager, null)
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

        return NimbusJwtDecoder
            .withIssuerLocation(
                "https://aam.localhost/auth/realms/dummy-realm"
            )
            .restOperations(RestTemplate())
            .build()
    }
}
