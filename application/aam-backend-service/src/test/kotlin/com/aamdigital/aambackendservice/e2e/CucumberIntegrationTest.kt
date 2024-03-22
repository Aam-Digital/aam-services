package com.aamdigital.aambackendservice.e2e

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.Assert
import java.io.File

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

    @Given("document {} is stored in database {}")
    fun `store a report`(document: String, database: String) {
        couchDbTestingService.createDocument(
            database = database,
            documentName = document,
            documentContent = File("src/test/resources/database/documents/$document.json").readText()
        )
    }

    @When("the client calls GET {}")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint`(endpoint: String) {
        fetch(endpoint)
    }

    @Then("the client receives an json array")
    @Throws(Throwable::class)
    fun `the client receives list of values`() {
        Assert.assertTrue(parseBodyToArrayNode()?.isArray ?: false)
    }

    @When("the client receives an json object")
    @Throws(Throwable::class)
    fun `the client receives an json object`() {
        Assert.assertTrue(parseBodyToObjectNode()?.isObject ?: false)
    }

    @Then("the client receives status code of {int}")
    @Throws(Throwable::class)
    fun `the client receives status code of`(statusCode: Int) {
        Assert.assertTrue(latestResponseStatus?.value() == statusCode)
    }

    @Then("the client receives value {} for property {}")
    @Throws(Throwable::class)
    fun `the client receives value for property`(value: String, property: String) {
        Assert.assertTrue(parseBodyToObjectNode()?.has(property) ?: false)
        Assert.assertEquals(value, parseBodyToObjectNode()?.get(property)?.textValue())
    }

    @Then("the client receives array with {int} elements")
    @Throws(Throwable::class)
    fun `the client receives array with n elements`(numberOfElements: Int) {
        Assert.assertEquals(numberOfElements, parseBodyToArrayNode()?.size())
    }
}
