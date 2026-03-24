package com.aamdigital.aambackendservice.e2e

import com.aamdigital.aambackendservice.common.changes.SyncRepository
import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import java.io.File

@CucumberContextConfiguration
class CucumberIntegrationTest(
    val reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher,
    val syncRepository: SyncRepository,
    val userDeviceRepository: UserDeviceRepository
) : SpringIntegrationTest() {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var storedId: String? = null

    @Before
    fun `log scenario start`() {
        logger.info("[CucumberTest] === Scenario starting ===")
        logger.info("[CucumberTest] SyncEntries before scenario: {}", syncRepository.findAll().map { "${it.database}=${it.latestRef.take(20)}" })
    }

    @After
    fun `reset all databases`() {
        logger.info("[CucumberTest] === Scenario cleanup ===")
        couchDbTestingService.reset()
        userDeviceRepository.deleteAll()
        storedId = null
        authToken = null
    }

    @Given("signed in as client {} with secret {} in realm {}")
    fun `sign in as user in realm`(
        client: String,
        secret: String,
        realm: String
    ) {
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
    fun `store attachment in document`(
        attachment: String,
        document: String,
        database: String
    ) {
        couchDbTestingService.addAttachment(
            database = database,
            documentName = document,
            attachmentName = "data.json", // fixed in business logic for now
            documentContent = File("src/test/resources/database/documents/$attachment.json").readText()
        )
    }

    @Given("document {} is stored in database {}")
    fun `store document in database`(
        document: String,
        database: String
    ) {
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
    fun `emit ReportCalculationEvent`(
        reportCalculationId: String,
        tenant: String
    ) {
        reportCalculationEventPublisher.publish(
            "report.calculation",
            ReportCalculationEvent(
//                tenant = tenant, // to prepare multi tenant
                reportCalculationId = reportCalculationId
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
    fun `the client issues POST endpoint with body`(
        endpoint: String,
        body: String
    ) {
        exchange(endpoint, HttpMethod.POST, File("src/test/resources/database/documents/$body.json").readText())
    }

    @When("the client calls POST {} with file {}")
    @Throws(Throwable::class)
    fun `the client issues POST endpoint with file`(
        endpoint: String,
        file: String
    ) {
        exchangeMultipart(endpoint, ClassPathResource("files/$file"))
    }

    @When("the client calls DELETE {word}")
    @Throws(Throwable::class)
    fun `the client issues DELETE endpoint`(endpoint: String) {
        exchange(endpoint, HttpMethod.DELETE)
    }

    @Given("the client stores the id from latest response")
    fun `store id from latest response`() {
        storedId = parseBodyToObjectNode()?.get("id")?.textValue()
            ?: throw AssertionError("Expected 'id' field in response but was not found")
    }

    @When("the client calls GET {} with stored id")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint with stored id`(endpoint: String) {
        exchange(endpoint + storedId, HttpMethod.GET)
    }

    @When("the client calls POST {} with stored id and suffix {}")
    @Throws(Throwable::class)
    fun `the client issues POST endpoint with stored id and suffix`(prefix: String, suffix: String) {
        exchange("$prefix$storedId$suffix", HttpMethod.POST)
    }

    @When("the client calls DELETE {} with stored id and suffix {}")
    @Throws(Throwable::class)
    fun `the client issues DELETE endpoint with stored id and suffix`(prefix: String, suffix: String) {
        exchange("$prefix$storedId$suffix", HttpMethod.DELETE)
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
    fun `the client receives value for header `(
        value: String,
        property: String
    ) {
        Assert.assertEquals(true, parseHeader(property).isNotEmpty())
        Assert.assertEquals(value, parseHeader(property).first())
    }

    @Then("the client receives value {} for property {}")
    @Throws(Throwable::class)
    fun `the client receives value for property`(
        value: String,
        property: String
    ) {
        Assert.assertEquals(true, parseBodyToObjectNode()?.has(property))
        val actualValue = parseBodyToObjectNode()?.get(property)?.textValue()
        if (value.contains("|")) {
            val acceptedValues = value
                .split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            Assert.assertTrue(
                "Expected one of $acceptedValues for property $property but was $actualValue",
                acceptedValues.contains(actualValue)
            )
        } else {
            Assert.assertEquals(value, actualValue)
        }
    }

    @Then("the client receives array with {int} elements")
    @Throws(Throwable::class)
    fun `the client receives array with n elements`(numberOfElements: Int) {
        Assert.assertEquals(numberOfElements, parseBodyToArrayNode()?.size())
    }

    @Then("the client receives property {} as array with {int} elements")
    @Throws(Throwable::class)
    fun `the client receives property as array with n elements`(property: String, numberOfElements: Int) {
        val arrayNode = parseBodyToObjectNode()?.get(property)
        Assert.assertNotNull("Property $property not found in response", arrayNode)
        Assert.assertEquals(numberOfElements, arrayNode?.size())
    }

    @Then("the client waits for {long} milliseconds")
    @Throws(Throwable::class)
    fun `the client for n milliseconds`(milliseconds: Long) =
        runBlocking {
            delay(milliseconds)
        }

    @Given("document {} is updated in database {}")
    fun `update document in database`(
        document: String,
        database: String
    ) {
        System.err.println("[CucumberTest] Updating document $document in database $database")
        couchDbTestingService.updateDocument(
            database = database,
            documentName = document,
            documentContent = java.io.File("src/test/resources/database/documents/$document.json").readText()
        )
        // Verify the update by fetching the document rev
        val docId = document.replaceFirst("_", ":")
        val docRev = couchDbTestingService.getDocumentRev(database, docId)
        System.err.println("[CucumberTest] After update, document $docId rev=$docRev")
    }

    @Then("user {word} has {int} notification(s) in CouchDB")
    fun `user has n notifications in CouchDB`(
        userId: String,
        expectedCount: Int
    ) {
        val maxWaitMs = 10_000L
        val pollIntervalMs = 500L
        val deadline = System.currentTimeMillis() + maxWaitMs
        var actualCount: Int

        val syncEntries = syncRepository.findAll().map { "${it.database}=${it.latestRef.take(30)}" }
        System.err.println("[CucumberTest] Waiting for $expectedCount notifications for user $userId (timeout: ${maxWaitMs}ms)")
        System.err.println("[CucumberTest] SyncEntries: $syncEntries")

        do {
            actualCount = couchDbTestingService.countDocuments("notifications_$userId")
            if (actualCount == expectedCount) break
            if (System.currentTimeMillis() >= deadline) break
            Thread.sleep(pollIntervalMs)
        } while (true)

        val syncEntriesAfter = syncRepository.findAll().map { "${it.database}=${it.latestRef.take(30)}" }
        System.err.println("[CucumberTest] SyncEntries after polling: $syncEntriesAfter")
        System.err.println("[CucumberTest] Final count for user $userId: $actualCount (expected: $expectedCount)")

        Assert.assertEquals(
            "Expected $expectedCount notifications for user $userId",
            expectedCount,
            actualCount
        )
    }
}
