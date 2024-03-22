Feature: the report calculation endpoint persist to database

    Scenario: client makes call to GET /reporting/report-calculation/report/ReportConfig:1 without token and receives Unauthorized
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        When the client calls GET /v1/reporting/report-calculation/report/ReportConfig:1
        Then the client receives an json object
        Then the client receives status code of 401

    Scenario: client makes call to GET /reporting/report-calculation/report/ReportConfig:1 and receives empty list
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/report/ReportConfig:1
        Then the client receives an json array
        Then the client receives status code of 200
        Then the client receives array with 0 elements
