Feature: the report calculation endpoint persist to database

    Scenario: client makes call to POST /reporting/report-calculation/report/ReportConfig:1 without token and receives Unauthorized
        Given database app is created
        Given database report-calculation is created
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
#        Then the client receives an json object # --> todo: some mismatchedInputException is thrown, needs analyse
        Then the client receives status code of 401

    Scenario: client makes call to POST /reporting/report-calculation/report/ReportConfig:1 and receives NotFound
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
        Then the client receives an json object
        Then the client receives status code of 404

    Scenario: client makes call to POST /reporting/report-calculation/report/ReportConfig:1 and receives CalculationId
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
        Then the client receives an json object
        Then the client receives status code of 200
        When the client calls GET /v1/reporting/report-calculation/ with id from latest response
        Then the client receives an json object
        Then the client receives status code of 200
