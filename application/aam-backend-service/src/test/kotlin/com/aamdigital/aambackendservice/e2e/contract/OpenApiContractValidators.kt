package com.aamdigital.aambackendservice.e2e.contract

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.LevelResolver
import com.atlassian.oai.validator.report.ValidationReport
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Validates e2e HTTP interactions against the hand-written OpenAPI specs in
 * `docs/api-specs/`, turning those specs into an enforced contract.
 *
 * One [OpenApiInteractionValidator] is built per feature module and selected by
 * matching the request path prefix (`/v1/<module>`). Calls that don't match a
 * known module (e.g. direct Carbone / CouchDB container calls) are skipped.
 *
 * Strictness is per-module and controlled by the `contract.strict.modules` system
 * property (comma-separated module keys, e.g. `-Dcontract.strict.modules=reporting`):
 * - strict module: a mismatch fails the test (throws [AssertionError]).
 * - non-strict (default): mismatches are logged as warnings only (report-only mode).
 */
object OpenApiContractValidators {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val strictModules: Set<String> =
        (System.getProperty("contract.strict.modules") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** A module's validator either loads, or fails to load (e.g. an invalid spec). */
    private sealed interface ValidatorState

    private class Loaded(
        val validator: OpenApiInteractionValidator
    ) : ValidatorState

    private class LoadFailed(
        val message: String
    ) : ValidatorState

    /** Built lazily and independently per module so one invalid spec can't break others. */
    private val validatorCache = ConcurrentHashMap<String, ValidatorState>()
    private val warnedLoadFailures = ConcurrentHashMap.newKeySet<String>()

    private fun stateFor(module: ContractModule): ValidatorState =
        validatorCache.computeIfAbsent(module.name) {
            try {
                Loaded(buildValidator(module))
            } catch (ex: Exception) {
                LoadFailed(ex.message ?: ex.toString())
            }
        }

    private fun buildValidator(module: ContractModule): OpenApiInteractionValidator {
        require(module.specFile.exists()) {
            "OpenAPI spec not found for module '${module.name}': ${module.specFile.absolutePath}"
        }
        return OpenApiInteractionValidator
            .createForSpecificationUrl(module.specFile.toURI().toString())
            // Documented paths are relative (e.g. /report); the app serves them under
            // /v1/<module>, and spec `servers:` urls are inconsistent across modules.
            .withBasePathOverride(module.prefix)
            .withLevelResolver(
                LevelResolver
                    .create()
                    // The e2e harness handles auth itself; ignore spec security checks.
                    .withLevel("validation.request.security.missing", ValidationReport.Level.IGNORE)
                    .withLevel("validation.request.security.invalid", ValidationReport.Level.IGNORE)
                    .build()
            ).build()
    }

    /**
     * Validate a single request/response interaction against the owning module spec.
     * No-op for paths that don't belong to a documented module.
     */
    fun validate(
        method: String,
        path: String,
        requestBody: String?,
        requestContentType: String?,
        statusCode: Int,
        responseBody: String?,
        responseContentType: String?
    ) {
        val module = ContractModule.forPath(path) ?: return
        val strict = strictModules.contains(module.name)

        val validator =
            when (val state = stateFor(module)) {
                is Loaded -> state.validator
                is LoadFailed -> {
                    val msg = "Could not load OpenAPI spec for module '${module.name}': ${state.message}"
                    if (strict) {
                        throw AssertionError(msg)
                    }
                    if (warnedLoadFailures.add(module.name)) {
                        logger.warn("[contract][report-only] {}", msg)
                    }
                    return
                }
            }

        val request = buildRequest(method, path, requestBody, requestContentType)
        val response = buildResponse(statusCode, responseBody, responseContentType)

        val report = validator.validate(request, response)

        ContractCoverageRecorder.record(module.name, method, path, statusCode)

        if (!report.hasErrors()) {
            return
        }

        val summary = report.messages.joinToString("\n") { "  - [${it.level}] ${it.key}: ${it.message}" }
        val header = "OpenAPI contract mismatch for [${module.name}] $method $path -> $statusCode"

        if (strict) {
            throw AssertionError("$header\n$summary")
        } else {
            logger.warn("[contract][report-only] {}\n{}", header, summary)
        }
    }

    private fun buildRequest(
        method: String,
        path: String,
        body: String?,
        contentType: String?
    ): SimpleRequest {
        val pathOnly = path.substringBefore('?')
        val builder = SimpleRequest.Builder(method, pathOnly)
        path
            .substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .forEach {
                val key = it.substringBefore('=')
                val value = it.substringAfter('=', "")
                builder.withQueryParam(key, value)
            }
        if (!body.isNullOrEmpty()) {
            builder.withContentType(contentType ?: "application/json")
            builder.withBody(body)
        }
        return builder.build()
    }

    private fun buildResponse(
        status: Int,
        body: String?,
        contentType: String?
    ): SimpleResponse {
        val builder = SimpleResponse.Builder(status)
        if (!contentType.isNullOrEmpty()) {
            builder.withContentType(contentType)
        }
        if (!body.isNullOrEmpty()) {
            builder.withBody(body)
        }
        return builder.build()
    }
}
