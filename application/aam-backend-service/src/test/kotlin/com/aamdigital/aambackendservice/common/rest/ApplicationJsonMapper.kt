package com.aamdigital.aambackendservice.common.rest

/**
 * The shared JSON mapper as the application configures it, for tests that do not start Spring.
 */
fun applicationJsonMapper() = ObjectMapperConfiguration().objectMapper()
