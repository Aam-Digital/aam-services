@Webhook
Feature: Webhook registration and subscription management

    Background:
        Given all default databases are created

    # Guardrail (regression test for the silent webhook outage): a report calculation that finishes
    # successfully must deliver to its subscribed webhooks. This deliberately spans the full chain
    # (calculation finished -> report.calculation.completed event -> notification -> notification.webhook
    # -> webhook trigger). Because the e2e change-detection allowlist only polls `app`, a completed
    # calculation in the `report-calculation` database is NOT observed via the CouchDB changes feed here:
    # this scenario therefore only passes when completion is announced by an explicit RabbitMQ event.
    Scenario: A successfully finished report calculation triggers its subscribed webhook
        Given document ReportConfig_1 is stored in database app
        Given document Config_CONFIG_ENTITY is stored in database app
        Given document ReportCalculation_2 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/webhook with body CreateWebhookRequest_1
        Then the client receives status code of 200
        Given the client stores the id from latest response
        When the client calls POST /v1/reporting/webhook/ with stored id and suffix /subscribe/report/ReportConfig:1
        Then the client receives status code of 200
        Given emit ReportCalculationEvent for ReportCalculation:2 in tenant local-spring
        Then the subscribed webhook is triggered

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
