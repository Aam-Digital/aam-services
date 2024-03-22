Feature: the report endpoints persist to database

    Scenario: client makes call to GET /reporting/report and receives empty list
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report
        Then the client receives an json array
        Then the client receives status code of 200
        Then the client receives array with 0 elements

    Scenario: client makes call to GET /reporting/report without token and receives Unauthorized
        Given database app is created
        Given database report-calculation is created
        When the client calls GET /v1/reporting/report
        Then the client receives an json object
        Then the client receives status code of 401

    Scenario: client makes call to GET /reporting/report/foo and receives not found
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report/foo
        Then the client receives an json object
        Then the client receives status code of 404

    Scenario: client makes call to GET /reporting/report/ReportConfig:1 and receives document
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report/ReportConfig:1
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value Test Report 1 for property name

    Scenario: client makes call to GET /reporting/report and receives two document
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        Given document ReportConfig:2 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report
        Then the client receives status code of 200
        Then the client receives an json array
        Then the client receives array with 2 elements
