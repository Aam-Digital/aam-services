package com.aamdigital.aambackendservice.skill.domain

import com.aamdigital.aambackendservice.common.rest.ObjectMapperConfiguration
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest(classes = [ObjectMapperConfiguration::class])
class SkillUsageTest {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `objectMapper feature READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE should be enabled`() {
        assertTrue(objectMapper.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE))
    }

    @Test
    fun `should parse invalid values to UNKNOWN with objectMapper`() {
        val result = objectMapper.convertValue("invalid-value", SkillUsage::class.java)
        assertEquals(SkillUsage.UNKNOWN, result)
    }

    @Test
    fun `should parse BI_WEEKLY correctly with objectMapper`() {
        val result = objectMapper.convertValue("BI-WEEKLY", SkillUsage::class.java)
        assertEquals(SkillUsage.BI_WEEKLY, result)
    }
}
