package com.aamdigital.aambackendservice.common.placeholder.di

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.UserPlaceholderValueProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PlaceholderConfiguration {
    /**
     * The [UserProfileProvider] only exists with Keycloak admin access. It is looked up when this bean
     * is created, after all bean definitions are registered, so this does not depend on the order in
     * which configuration classes are processed (as `@ConditionalOnBean` would).
     */
    @Bean
    fun placeholderValueProvider(userProfileProvider: ObjectProvider<UserProfileProvider>): PlaceholderValueProvider =
        UserPlaceholderValueProvider(userProfileProvider.getIfAvailable())
}
