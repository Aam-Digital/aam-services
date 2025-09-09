package com.aamdigital.aambackendservice.common.actuator

/**
 * Interface for feature modules to provide their feature information
 */
interface FeatureRegistrar {
    /**
     * Returns the feature name and its status information
     */
    fun getFeatureInfo(): Pair<String, FeaturesInfoDto>
}
