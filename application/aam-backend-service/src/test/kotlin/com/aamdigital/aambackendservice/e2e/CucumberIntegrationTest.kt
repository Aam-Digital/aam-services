package com.aamdigital.aambackendservice.e2e

import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.reporting.domain.event.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import java.io.File

@CucumberContextConfiguration
class CucumberIntegrationTest(
    val reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher,
) : SpringIntegrationTest() {

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

    @Given("attachment {} added to document {} in {}")
    fun `store attachment in document`(attachment: String, document: String, database: String) {
        couchDbTestingService.addAttachment(
            database = database,
            documentName = document,
            attachmentName = "data.json", // fixed in business logic for now
            documentContent = File("src/test/resources/database/documents/$attachment.json").readText()
        )
    }

    @Given("document {} is stored in database {}")
    fun `store document in database`(document: String, database: String) {
        couchDbTestingService.createDocument(
            database = database,
            documentName = document,
            documentContent = File("src/test/resources/database/documents/$document.json").readText()
        )
    }

    @Given("template {} is stored in template engine")
    fun `store template in template engine`(file: String) {
        exchangeMultipart(
            "http://${TestContainers.CONTAINER_PDF.host}:${TestContainers.CONTAINER_PDF.getMappedPort(4000)}/template",
            ClassPathResource("files/$file")
        )
    }

    @When("the client calls GET {word}")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint`(endpoint: String) {
        exchange(endpoint, HttpMethod.GET)
    }

    @Given("emit ReportCalculationEvent for {word} in tenant {word}")
    @Throws(Throwable::class)
    fun `emit ReportCalculationEvent`(reportCalculationId: String, tenant: String) {
        reportCalculationEventPublisher.publish(
            "report.calculation",
            ReportCalculationEvent(
//                tenant = tenant, // to prepare multi tenant
                reportCalculationId = reportCalculationId,
            )
        )
    }

    @When("the client calls GET {word} with id from latest response")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint with id from latest response`(endpoint: String) {
        exchange(endpoint + parseBodyToObjectNode()?.get("id")?.textValue(), HttpMethod.GET)
    }

    @When("the client calls POST {} without body")
    @Throws(Throwable::class)
    fun `the client issues POST endpoint without body`(endpoint: String) {
        exchange(endpoint, HttpMethod.POST)
    }


    @When("the client calls POST {} with body {}")
    @Throws(Throwable::class)
    fun `the client issues POST endpoint with body`(endpoint: String, body: String) {
        exchange(endpoint, HttpMethod.POST, File("src/test/resources/database/documents/$body.json").readText())
    }

    @When("the client calls POST {} with file {}")
    @Throws(Throwable::class)
    fun `the client issues POST endpoint with file`(endpoint: String, file: String) {
        exchangeMultipart(endpoint, ClassPathResource("files/$file"))
    }

    @Then("the client receives a json array")
    @Throws(Throwable::class)
    fun `the client receives list of values`() {
        Assert.assertEquals(true, parseBodyToArrayNode()?.isArray)
    }

    @When("the client receives an json object")
    @Throws(Throwable::class)
    fun `the client receives an json object`() {
        Assert.assertEquals(true, parseBodyToObjectNode()?.isObject)
    }

    @Then("the client receives status code of {int}")
    @Throws(Throwable::class)
    fun `the client receives status code of`(statusCode: Int) {
        Assert.assertEquals(statusCode, latestResponseStatus?.value())
    }

    @Then("the client receives value {} for header {}")
    @Throws(Throwable::class)
    fun `the client receives value for header `(value: String, property: String) {
        Assert.assertEquals(true, parseHeader(property).isNotEmpty())
        Assert.assertEquals(value, parseHeader(property).first())
    }

    @Then("the client receives value {} for property {}")
    @Throws(Throwable::class)
    fun `the client receives value for property`(value: String, property: String) {
        Assert.assertEquals(true, parseBodyToObjectNode()?.has(property))
        Assert.assertEquals(value, parseBodyToObjectNode()?.get(property)?.textValue())
    }

    @Then("the client receives array with {int} elements")
    @Throws(Throwable::class)
    fun `the client receives array with n elements`(numberOfElements: Int) {
        Assert.assertEquals(numberOfElements, parseBodyToArrayNode()?.size())
    }

    @Then("the client waits for {long} milliseconds")
    @Throws(Throwable::class)
    fun `the client for n milliseconds`(milliseconds: Long) = runBlocking {
        delay(milliseconds)
    }
}
