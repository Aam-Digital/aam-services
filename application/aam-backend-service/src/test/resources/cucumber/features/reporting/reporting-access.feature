Feature: the reporting endpoints require the reporting client scopes

    # dummy-client has reporting_read / reporting_write only as Optional client scopes in the imported realm;
    # on startup the backend makes them Default scopes, so its tokens carry them without requesting them.

    Background:
        Given database app is created
        Given database report-calculation is created

    Scenario: API client with the reporting client scopes can list reports
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        Then the access token contains client scope reporting_read
        Then the access token contains client scope reporting_write
        When the client calls GET /v1/reporting/report
        Then the client receives status code of 200

    Scenario: API client without reporting_read client scope cannot list reports
        Given signed in as client no-scope-client with secret no-scope-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report
        Then the client receives an json object
        Then the client receives status code of 403
        Then the client receives value insufficient_scope for property errorCode

    Scenario: API client without reporting_write client scope cannot start a report calculation
        Given document ReportConfig_1 is stored in database app
        Given signed in as client no-scope-client with secret no-scope-secret in realm dummy-realm
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
        Then the client receives an json object
        Then the client receives status code of 403

    Scenario: user of the frontend app can list reports without reporting client scopes
        Given signed in as user app-user with password app-user-password through client app in realm dummy-realm
        When the client calls GET /v1/reporting/report
        Then the client receives a json array
        Then the client receives status code of 200

    Scenario: user of the frontend app can start a report calculation without reporting client scopes
        Given document ReportConfig_1 is stored in database app
        Given signed in as user app-user with password app-user-password through client app in realm dummy-realm
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
        Then the client receives an json object
        Then the client receives status code of 200
