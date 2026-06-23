package com.aamdigital.aambackendservice.e2e.contract

import java.util.concurrent.ConcurrentHashMap

/**
 * Records which API interactions the e2e suite actually exercised, so a suite-end
 * gate can assert that every documented operation of a strict module was covered.
 *
 * Populated by [OpenApiContractValidators.validate] during the run; consumed after
 * the suite finishes (see the Cucumber `TestRunFinished` enforcement plugin).
 */
object ContractCoverageRecorder {
    /** A single exercised interaction (raw request path, not yet a spec template). */
    data class Hit(
        val module: String,
        val method: String,
        val path: String,
        val statusCode: Int
    )

    private val hits = ConcurrentHashMap.newKeySet<Hit>()

    fun record(
        module: String,
        method: String,
        path: String,
        statusCode: Int
    ) {
        hits.add(Hit(module, method.uppercase(), path.substringBefore('?'), statusCode))
    }

    fun hitsForModule(module: String): Set<Hit> = hits.filter { it.module == module }.toSet()

    fun reset() = hits.clear()
}
