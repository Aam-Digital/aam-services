package com.aamdigital.aambackendservice.e2e.contract

import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.parser.OpenAPIV3Parser
import java.io.File

/**
 * The set of operations a module's OpenAPI spec documents, used by the coverage and
 * inventory gates to compare against tested / implemented endpoints.
 */
class ContractSpecModel(
    specFile: File
) {
    private data class SpecOperation(
        val method: String,
        val template: String
    )

    private val operations: List<SpecOperation>

    init {
        val openApi =
            OpenAPIV3Parser().read(specFile.toURI().toString())
                ?: error("Could not parse OpenAPI spec: ${specFile.absolutePath}")
        operations =
            (openApi.paths ?: emptyMap<String, PathItem>()).flatMap { (path, item) ->
                item.readOperationsMap().keys.map { httpMethod -> SpecOperation(httpMethod.name, path) }
            }
    }

    /** Canonical keys for every documented operation, e.g. `GET /report/{}`. */
    val documentedOperationKeys: Set<String> =
        operations.map { operationKey(it.method, it.template) }.toSet()

    /** Returns the documented operation key matching a concrete request, or null. */
    fun match(
        method: String,
        concretePath: String
    ): String? =
        operations
            .firstOrNull {
                it.method.equals(
                    method,
                    ignoreCase = true
                ) &&
                    pathMatchesTemplate(it.template, concretePath)
            }?.let { operationKey(it.method, it.template) }
}
