package com.aamdigital.aambackendservice.notification.domain

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty

enum class NotificationType {
    @JsonProperty("entity_change")
    ENTITY_CHANGE,
    
    @JsonEnumDefaultValue
    UNKNOWN,
}
