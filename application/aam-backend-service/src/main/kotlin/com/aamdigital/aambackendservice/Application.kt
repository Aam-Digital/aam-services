package com.aamdigital.aambackendservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
class HelloFriendController {
    @GetMapping("/")
    @Suppress("FunctionOnlyReturningConstant")
    fun helloFriend(): String {
        return "Hello, Friend. This is aam-backend-service."
    }
}
