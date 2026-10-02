package com.aamdigital.aambackendservice.common.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * The shared [ObjectMapper], which Spring Boot also wraps in the JSON message converter of Spring MVC
 * and of its `RestClient.Builder`.
 *
 * There is deliberately no `HttpMessageConverter` bean for it: Spring Boot 4 puts such beans ahead of
 * all default converters, so Jackson would answer String and Resource bodies too, instead of the
 * converters that pass them through as they are.
 */
@Configuration
class ObjectMapperConfiguration {
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val mapper = Jackson2ObjectMapperBuilder()
        mapper.featuresToEnable(
            DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT,
            DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE
        )
        return mapper.build()
    }
}
