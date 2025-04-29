package com.aamdigital.aambackendservice.notification.domain

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

enum class NotificationChannelType {
    APP,
    PUSH,
    EMAIL,

    @JsonEnumDefaultValue
    UNKNOWN,
}
