package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailEnabled
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching
@ConditionalOnNotificationEmailEnabled
class NotificationEmailCacheConfiguration {
    @Bean("notificationEmailCacheManager")
    fun notificationEmailCacheManager(): CacheManager {
        val caffeine: Caffeine<Any, Any> =
            Caffeine
                .newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
        return CaffeineCacheManager("user-emails").also { it.setCaffeine(caffeine) }
    }
}
