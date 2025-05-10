package com.aamdigital.aambackendservice.common.crypto.di

import com.aamdigital.aambackendservice.common.crypto.core.CryptoConfig
import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
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
