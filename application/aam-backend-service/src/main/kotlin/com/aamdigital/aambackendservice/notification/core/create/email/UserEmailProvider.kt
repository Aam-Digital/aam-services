package com.aamdigital.aambackendservice.notification.core.create.email

interface UserEmailProvider {
    fun lookupEmail(userIdentifier: String): String?
}
