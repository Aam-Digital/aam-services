package com.aamdigital.aambackendservice.e2e

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["src/test/resources"],
    // Suite-end OpenAPI coverage + inventory enforcement for strict modules.
    plugin = ["com.aamdigital.aambackendservice.e2e.contract.ContractEnforcementPlugin"]
)
class CucumberTestRunner
