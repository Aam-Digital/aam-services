package com.aamdigital.aambackendservice.e2e.contract

import io.cucumber.plugin.ConcurrentEventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.TestRunFinished
import io.cucumber.plugin.event.TestRunStarted

/**
 * Suite-end enforcement of the OpenAPI contract for strict modules (set via the
 * `contract.strict.modules` system property). Runs on Cucumber's `TestRunFinished`
 * event — the only reliable suite-end hook for the JUnit-4 `@RunWith(Cucumber)`
 * runner — and fails the run if, for any strict module:
 *
 * - **Coverage:** a documented operation was never exercised by an e2e scenario.
 * - **Inventory:** a controller endpoint is missing from the OpenAPI spec.
 *
 * Per-request conformance is enforced separately in [OpenApiContractValidators].
 */
class ContractEnforcementPlugin : ConcurrentEventListener {
    private val strictModules: Set<String> =
        (System.getProperty("contract.strict.modules") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    override fun setEventPublisher(publisher: EventPublisher) {
        publisher.registerHandlerFor(TestRunStarted::class.java) { _ -> ContractCoverageRecorder.reset() }
        publisher.registerHandlerFor(TestRunFinished::class.java) { _ -> enforce() }
    }

    private fun enforce() {
        val strictGates = ContractModule.ALL.filter { strictModules.contains(it.name) }
        if (strictGates.isEmpty()) return

        val failures =
            strictGates.flatMap { gate ->
                val spec = ContractSpecModel(gate.specFile)
                checkCoverage(gate, spec) + checkInventory(gate, spec)
            }

        if (failures.isNotEmpty()) {
            throw AssertionError("OpenAPI contract gate failed:\n\n" + failures.joinToString("\n\n"))
        }
    }

    private fun checkCoverage(
        gate: ContractModule,
        spec: ContractSpecModel
    ): List<String> {
        val exercised =
            ContractCoverageRecorder
                .hitsForModule(gate.name)
                .mapNotNull { spec.match(it.method, it.path.removePrefix(gate.prefix).ifEmpty { "/" }) }
                .toSet()
        val uncovered = (spec.documentedOperationKeys - exercised).sorted()
        return if (uncovered.isEmpty()) {
            emptyList()
        } else {
            listOf(
                "[${gate.name}] documented operations never exercised by an e2e test " +
                        "(add a scenario, or remove from the spec):\n" +
                        uncovered.joinToString("\n") { "    - $it" }
            )
        }
    }

    private fun checkInventory(
        gate: ContractModule,
        spec: ContractSpecModel
    ): List<String> {
        val undocumented =
            (
                    ControllerEndpointScanner.endpointKeys(gate.basePackage, gate.prefix) - spec.documentedOperationKeys
                    ).sorted()
        return if (undocumented.isEmpty()) {
            emptyList()
        } else {
            listOf(
                "[${gate.name}] endpoints implemented in code but missing from the OpenAPI spec " +
                        "(document them in docs/api-specs/${gate.specFileName}):\n" +
                        undocumented.joinToString("\n") { "    - $it" }
            )
        }
    }
}
