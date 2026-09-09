package com.aamdigital.aambackendservice.e2e

import com.aamdigital.aambackendservice.common.changes.SyncRepository
import com.aamdigital.aambackendservice.common.couchdb.core.BACKEND_STATE_DATABASE
import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.mail.MailSenderResponse
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.container.TestContainers
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.create.email.UserEmailProvider
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationEvent
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import com.aamdigital.aambackendservice.reporting.webhook.core.TriggerWebhookUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.UserModel
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.spring.CucumberContextConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.mockito.kotlin.any
import org.mockito.kotlin.after
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import java.io.File
import java.util.Optional

@CucumberContextConfiguration
class CucumberIntegrationTest(
    val reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher,
    val syncRepository: SyncRepository,
    val notificationConfigCache: NotificationConfigCache
) : SpringIntegrationTest() {
    private val logger = LoggerFactory.getLogger(javaClass)

    @MockBean
    lateinit var mailSenderService: MailSenderService

    @MockBean
    lateinit var userEmailProvider: UserEmailProvider

    // mocked so the guardrail can verify the webhook is triggered without needing a real HTTP receiver;
    // the mock still sits downstream of both the report.calculation.completed and notification.webhook queues
    @MockBean
    lateinit var triggerWebhookUseCase: TriggerWebhookUseCase

    // mocked so the SSO scenarios never need a reachable Keycloak admin client: the provider is the
    // only part of third-party-authentication that talks to Keycloak, everything downstream of it
    // (session storage, redirect binding, HTTP contract) stays real.
    @MockBean
    lateinit var authenticationProvider: AuthenticationProvider

    private var storedId: String? = null
    private var latestNotificationConfigUserIdentifier: String? = null
    private var storedSessionId: String? = null
    private var storedSessionToken: String? = null

    @Before
    fun `log scenario start`() {
        reset(mailSenderService, userEmailProvider, triggerWebhookUseCase, authenticationProvider)
        whenever(userEmailProvider.lookupEmail(any())).thenReturn("integration-test-user@example.com")
        whenever(mailSenderService.sendMail(any<MailSenderRequest>())).thenReturn(MailSenderResponse(success = true))
        // a scenario that starts a session declares which account it means; this default only keeps
        // an unstubbed call from failing with a confusing NullPointerException
        whenever(authenticationProvider.findByEmail(any()))
            .thenReturn(Optional.of(externalUser("unstubbed-external-user")))

        logger.info("[CucumberTest] === Scenario starting ===")
        logger.info("[CucumberTest] SyncEntries before scenario: {}", syncRepository.findAll().map { "${it.database}=${it.latestRef.take(20)}" })
    }

    @After
    fun `reset all databases`() {
        logger.info("[CucumberTest] === Scenario cleanup ===")
        couchDbTestingService.reset()
        // aam-backend-state survives reset() (see CouchDbTestingService), so the per-scenario
        // state inside it is cleared explicitly
        couchDbTestingService.deleteDocumentsByPrefix(BACKEND_STATE_DATABASE, "UserDevice")
        storedId = null
        latestNotificationConfigUserIdentifier = null
        storedSessionId = null
        storedSessionToken = null
        authToken = null
        authSubject = null
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

        if (document.startsWith("NotificationConfig_")) {
            latestNotificationConfigUserIdentifier = document.removePrefix("NotificationConfig_")
        }
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

    @When("the client downloads GET {word}")
    @Throws(Throwable::class)
    fun `the client downloads GET endpoint`(endpoint: String) {
        download(endpoint)
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

    @Then("the client waits until the notification config is applied")
    fun `the client waits until the notification config is applied`() {
        val userIdentifier = latestNotificationConfigUserIdentifier
            ?: throw AssertionError("Expected a NotificationConfig document to be stored before waiting for config application")

        waitUntil(
            timeoutMs = 20_000L,
            pollIntervalMs = 250L,
            description = "notification config for user $userIdentifier to be applied"
        ) {
            notificationConfigCache.findAll().any {
                it.userIdentifier == userIdentifier && it.channelEmail
            }
        }
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

    @Then("email notification is sent {int} times")
    fun `email notification is sent n times`(expectedCount: Int) {
        verify(mailSenderService, after(10_000).times(expectedCount)).sendMail(any())
    }

    // Assert at-least-once (not an exact count): subscribing a webhook already triggers an initial
    // calculation, and the explicit emit triggers another, so multiple deliveries are expected. The
    // guardrail's point is that a finished calculation delivers to the webhook at all (never zero).
    @Then("the subscribed webhook is triggered")
    fun `the subscribed webhook is triggered`() {
        verify(triggerWebhookUseCase, timeout(10_000).atLeastOnce()).trigger(any())
    }

    /**
     * Binds the SSO session to the account the test itself is signed in as, so that the
     * redirect endpoint - which compares the stored userId against `principal.name` - accepts it.
     */
    @Given("the external user account already exists")
    fun `the external user account already exists`() {
        whenever(authenticationProvider.findByEmail(any()))
            .thenReturn(Optional.of(externalUser(requireAuthSubject())))
    }

    @Given("the external user account already exists for another user")
    fun `the external user account already exists for another user`() {
        whenever(authenticationProvider.findByEmail(any()))
            .thenReturn(Optional.of(externalUser("a-different-keycloak-user-id")))
    }

    @Given("the external user account does not exist yet")
    fun `the external user account does not exist yet`() {
        val userId = requireAuthSubject()
        whenever(authenticationProvider.findByEmail(any())).thenReturn(Optional.empty())
        whenever(
            authenticationProvider.createExternalUser(any(), any(), any(), any(), anyOrNull())
        ).thenReturn(externalUser(userId))
    }

    @Then("a new account is created in the authentication system")
    fun `a new account is created in the authentication system`() {
        verify(authenticationProvider).createExternalUser(any(), any(), any(), any(), anyOrNull())
    }

    @Given("the client stores the session from the latest response")
    fun `store session from latest response`() {
        val body = parseBodyToObjectNode()
            ?: throw AssertionError("Expected a session response body but none was received")
        storedSessionId = body.get("sessionId")?.textValue()
            ?: throw AssertionError("Expected 'sessionId' field in response but was not found")
        storedSessionToken = body.get("sessionToken")?.textValue()
            ?: throw AssertionError("Expected 'sessionToken' field in response but was not found")
    }

    @When("the client calls GET {} with stored session id and session token")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint with stored session id and session token`(prefix: String) {
        exchange("$prefix${requireStoredSessionId()}?session_token=${requireStoredSessionToken()}", HttpMethod.GET)
    }

    @When("the client calls GET {} with stored session id and session token {word}")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint with stored session id and given session token`(
        prefix: String,
        sessionToken: String
    ) {
        exchange("$prefix${requireStoredSessionId()}?session_token=$sessionToken", HttpMethod.GET)
    }

    @When("the client calls GET {} with stored session id and suffix {}")
    @Throws(Throwable::class)
    fun `the client issues GET endpoint with stored session id and suffix`(
        prefix: String,
        suffix: String
    ) {
        exchange("$prefix${requireStoredSessionId()}$suffix", HttpMethod.GET)
    }

    @Then("the client receives a non-empty value for property {word}")
    @Throws(Throwable::class)
    fun `the client receives a non-empty value for property`(property: String) {
        val value = parseBodyToObjectNode()?.get(property)
        Assert.assertNotNull("Property $property not found in response", value)
        Assert.assertTrue("Property $property is empty", value!!.asText().isNotBlank())
    }

    @Then("the client receives the signed-in user id for property {word}")
    @Throws(Throwable::class)
    fun `the client receives the signed-in user id for property`(property: String) {
        Assert.assertEquals(requireAuthSubject(), parseBodyToObjectNode()?.get(property)?.textValue())
    }

    @Then("database {word} contains {int} document(s)")
    fun `database contains n documents`(
        database: String,
        expectedCount: Int
    ) {
        Assert.assertEquals(
            "Expected $expectedCount document(s) in database $database",
            expectedCount,
            couchDbTestingService.countDocuments(database)
        )
    }

    private fun externalUser(userId: String) =
        UserModel(
            userId = userId,
            userName = "external-user",
            firstName = "Ada",
            lastName = "Lovelace",
            email = "ada.lovelace@example.com"
        )

    private fun requireAuthSubject(): String =
        authSubject ?: throw AssertionError("No signed-in user; the scenario must sign in first")

    private fun requireStoredSessionId(): String =
        storedSessionId ?: throw AssertionError("No stored session; store the session from a response first")

    private fun requireStoredSessionToken(): String =
        storedSessionToken ?: throw AssertionError("No stored session; store the session from a response first")

    private fun waitUntil(
        timeoutMs: Long,
        pollIntervalMs: Long,
        description: String,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }

            Thread.sleep(pollIntervalMs)
        }

        Assert.fail("Timed out after ${timeoutMs}ms while waiting for $description")
    }
}
