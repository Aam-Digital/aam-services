package com.aamdigital.aambackendservice.e2e.contract

import java.io.File

/**
 * Single source of truth for the feature modules covered by contract testing.
 *
 * Everything about a module is derived from its name by convention, so adding a
 * module is a one-line change in [ALL]:
 * - URL prefix:   `/v1/<name>`
 * - OpenAPI spec: `docs/api-specs/<name>-api-v1.yaml`
 * - code package: `com.aamdigital.aambackendservice.<name without hyphens>`
 *
 * The `<name without hyphens>` rule only affects multi-word modules such as
 * `third-party-authentication` (package `thirdpartyauthentication`).
 */
data class ContractModule(
    val name: String
) {
    val prefix: String = "/v1/$name"
    val specFileName: String = "$name-api-v1.yaml"
    val specFile: File = File(SPEC_DIR, specFileName)
    val basePackage: String = "$BASE_PACKAGE.${name.replace("-", "")}"

    companion object {
        private const val BASE_PACKAGE = "com.aamdigital.aambackendservice"

        /**
         * Specs live in `docs/api-specs/`, outside this Gradle module. Tests run with
         * the working directory set to `application/aam-backend-service/`.
         */
        private val SPEC_DIR = File("../../docs/api-specs")

        val ALL: List<ContractModule> =
            listOf(
                "reporting",
                "export",
                "notification",
                "skill",
                "third-party-authentication"
            ).map(::ContractModule)

        /** The module owning a request path (by `/v1/<name>` prefix), or null. */
        fun forPath(path: String): ContractModule? {
            val pathOnly = path.substringBefore('?')
            return ALL.firstOrNull { pathOnly == it.prefix || pathOnly.startsWith(it.prefix + "/") }
        }
    }
}
