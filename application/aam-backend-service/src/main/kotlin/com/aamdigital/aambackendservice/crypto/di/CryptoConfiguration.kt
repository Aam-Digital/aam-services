package com.aamdigital.aambackendservice.crypto.di

import com.aamdigital.aambackendservice.crypto.core.CryptoConfig
import com.aamdigital.aambackendservice.crypto.core.CryptoService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CryptoConfiguration {

    @Bean
    fun defaultCryptoService(
        config: CryptoConfig
    ): CryptoService =
        CryptoService(config)
}
