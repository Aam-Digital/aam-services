package com.aamdigital.aambackendservice.e2e

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.Assert
import org.springframework.http.ResponseEntity

@CucumberContextConfiguration
class CucumberIntegrationTest : SpringIntegrationTest() {

    @After
    fun `reset all databases`() {
        couchDbTestingService.reset()
    }

    @Given("signed in as client {} with secret {} in realm {}")
    fun `sign in as user in realm`(client: String, secret: String, realm: String) {
        fetchToken(client, secret, realm)
    }

    @Given("all default databases are created")
    fun `create default databases`() {
        couchDbTestingService.initDefaultDatabases()
    }

    @Given("database {word} is created")
    fun `create database for `(name: String) {
        couchDbTestingService.createDatabase(name)
    }

    @When("the client calls GET {} and assumes an object response")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint and assumes an object response`(endpoint: String) {
        getObjectNode(endpoint)
    }

    @When("the client calls GET {} and assumes an array response")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint and assumes an array response`(endpoint: String) {
        getArrayNode(endpoint)
    }

    @Then("the client receives status code of {int}")
    @Throws(Throwable::class)
    fun `the client receives status code of`(statusCode: Int) {
        Assert.assertTrue((latestResponse as ResponseEntity<*>?)?.statusCode?.value() == statusCode)
    }

    @Then("the client receives value {} for property {}")
    @Throws(Throwable::class)
    fun `the client receives value for property`(value: String, property: String) {
        Assert.assertTrue(((latestResponseBody as ObjectNode).has(property)))
        Assert.assertEquals(
            ((latestResponseBody as ObjectNode).get(property)?.textValue()),
            value
        )
    }

    @Then("the client receives list of values")
    @Throws(Throwable::class)
    fun `the client receives list of values`() {
        Assert.assertTrue((latestResponseBody as ArrayNode).isArray)
    }

    @Then("the client receives array with {int} elements")
    @Throws(Throwable::class)
    fun `the client receives array with n elements`(numberOfElements: Int) {
        Assert.assertEquals((latestResponseBody as ArrayNode?)?.size(), numberOfElements)
    }
}
