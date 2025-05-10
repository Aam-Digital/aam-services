package com.aamdigital.aambackendservice.common.rest

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest(classes = [ObjectMapperConfiguration::class])
class ObjectMapperConfigurationTest {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `objectMapper should not be null`() {
        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `objectMapper feature ACCEPT_EMPTY_STRING_AS_NULL_OBJECT should be enabled`() {
        TestCase.assertTrue(
            objectMapper.isEnabled(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )
        )
    }

    @Test
    fun `objectMapper feature READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE should be enabled`() {
        TestCase.assertTrue(
            objectMapper.isEnabled(
                DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE
            )
        )
    }
}
