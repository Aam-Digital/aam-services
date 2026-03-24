@Webhook
Feature: Webhook registration and subscription management

    Background:
        Given all default databases are created

    Scenario: Create a webhook without authentication returns 401
        When the client calls POST /v1/reporting/webhook with body CreateWebhookRequest_1
        Then the client receives status code of 401

    Scenario: Create a new webhook returns webhook id
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/webhook with body CreateWebhookRequest_1
        Then the client receives status code of 200
        When the client calls GET /v1/reporting/webhook/ with id from latest response
        Then the client receives status code of 200
        Then the client receives value Test Webhook for property label

    Scenario: List webhooks returns created webhooks
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/webhook with body CreateWebhookRequest_1
        Then the client receives status code of 200
        When the client calls GET /v1/reporting/webhook
        Then the client receives status code of 200
        Then the client receives a json array
        Then the client receives array with 1 elements

    Scenario: List webhooks without authentication returns 401
        When the client calls GET /v1/reporting/webhook
        Then the client receives status code of 401

    Scenario: Fetch a non-existent webhook returns 404
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/webhook/non-existent-id
        Then the client receives status code of 404

    Scenario: Subscribe webhook to a report
        Given document ReportConfig_1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/webhook with body CreateWebhookRequest_1
        Then the client receives status code of 200
        Given the client stores the id from latest response
        When the client calls POST /v1/reporting/webhook/ with stored id and suffix /subscribe/report/ReportConfig:1
        Then the client receives status code of 200
        When the client calls GET /v1/reporting/webhook/ with stored id
        Then the client receives status code of 200
        Then the client receives property reportSubscriptions as array with 1 elements

    Scenario: Unsubscribe webhook from a report
        Given document ReportConfig_1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/webhook with body CreateWebhookRequest_1
        Then the client receives status code of 200
        Given the client stores the id from latest response
        When the client calls POST /v1/reporting/webhook/ with stored id and suffix /subscribe/report/ReportConfig:1
        Then the client receives status code of 200
        When the client calls DELETE /v1/reporting/webhook/ with stored id and suffix /subscribe/report/ReportConfig:1
        Then the client receives status code of 200
        When the client calls GET /v1/reporting/webhook/ with stored id
        Then the client receives status code of 200
        Then the client receives property reportSubscriptions as array with 0 elements
