package com.aamdigital.aambackendservice.audit

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Reports the "audit" (change-logging) feature via `/actuator/features` so the
 * frontend can show/hide the change-history UI accordingly.
 *
 * Note: the audit feature itself is implemented in the replication-backend
 * (its `AUDIT_ENABLED` flag). This flag only mirrors that for the frontend, so
 * `features.audit.enabled` (env `FEATURES_AUDIT_ENABLED`) must be kept in sync
 * with the replication-backend's `AUDIT_ENABLED`.
 */
@Component
@ConditionalOnProperty(
    prefix = "features.audit",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AuditFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "audit" to FeaturesInfoDto(true)
}
