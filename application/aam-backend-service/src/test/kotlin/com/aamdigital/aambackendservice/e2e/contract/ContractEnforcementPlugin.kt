package com.aamdigital.aambackendservice.e2e.contract

import io.cucumber.plugin.ConcurrentEventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.TestRunFinished
import java.io.File

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
    private data class ModuleGate(
        val module: String,
        val prefix: String,
        val specFileName: String,
        val basePackage: String
    )

    private val gates =
        listOf(
            ModuleGate(
                "reporting",
                "/v1/reporting",
                "reporting-api-v1.yaml",
                "com.aamdigital.aambackendservice.reporting"
            ),
            ModuleGate("export", "/v1/export", "export-api-v1.yaml", "com.aamdigital.aambackendservice.export"),
            ModuleGate(
                "notification",
                "/v1/notification",
                "notification-api-v1.yaml",
                "com.aamdigital.aambackendservice.notification"
            ),
            ModuleGate("skill", "/v1/skill", "skill-api-v1.yaml", "com.aamdigital.aambackendservice.skill"),
            ModuleGate(
                "third-party-authentication",
                "/v1/third-party-authentication",
                "third-party-authentication-api-v1.yaml",
                "com.aamdigital.aambackendservice.thirdpartyauthentication"
            )
        )

    private val specDir = File("../../docs/api-specs")

    private val strictModules: Set<String> =
        (System.getProperty("contract.strict.modules") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    override fun setEventPublisher(publisher: EventPublisher) {
        publisher.registerHandlerFor(TestRunFinished::class.java) { _ -> enforce() }
    }

    private fun enforce() {
        val strictGates = gates.filter { strictModules.contains(it.module) }
        if (strictGates.isEmpty()) return

        val failures =
            strictGates.flatMap { gate ->
                val spec = ContractSpecModel(File(specDir, gate.specFileName))
                checkCoverage(gate, spec) + checkInventory(gate, spec)
            }

        if (failures.isNotEmpty()) {
            throw AssertionError("OpenAPI contract gate failed:\n\n" + failures.joinToString("\n\n"))
        }
    }

    private fun checkCoverage(
        gate: ModuleGate,
        spec: ContractSpecModel
    ): List<String> {
        val exercised =
            ContractCoverageRecorder
                .hitsForModule(gate.module)
                .mapNotNull { spec.match(it.method, it.path.removePrefix(gate.prefix).ifEmpty { "/" }) }
                .toSet()
        val uncovered = (spec.documentedOperationKeys - exercised).sorted()
        return if (uncovered.isEmpty()) {
            emptyList()
        } else {
            listOf(
                "[${gate.module}] documented operations never exercised by an e2e test " +
                    "(add a scenario, or remove from the spec):\n" +
                    uncovered.joinToString("\n") { "    - $it" }
            )
        }
    }

    private fun checkInventory(
        gate: ModuleGate,
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
                "[${gate.module}] endpoints implemented in code but missing from the OpenAPI spec " +
                    "(document them in docs/api-specs/${gate.specFileName}):\n" +
                    undocumented.joinToString("\n") { "    - $it" }
            )
        }
    }
}
