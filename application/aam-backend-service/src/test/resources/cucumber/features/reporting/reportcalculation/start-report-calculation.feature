Feature: the report calculation endpoint persist to database

    Background:
        Given database app is created
        Given database report-calculation is created

    Scenario: client makes call to POST /reporting/report-calculation/report/ReportConfig:1 without token and receives Unauthorized
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
#        Then the client receives an json object # --> todo: some mismatchedInputException is thrown, needs analyse
        Then the client receives status code of 401

    Scenario: client makes call to POST /reporting/report-calculation/report/ReportConfig:1 and receives NotFound
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
        Then the client receives an json object
        Then the client receives status code of 404

    Scenario: client makes call to POST /reporting/report-calculation/report/ReportConfig:1 and receives CalculationId
        Given document ReportConfig:1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/reporting/report-calculation/report/ReportConfig:1 without body
        Then the client receives an json object
        Then the client receives status code of 200
        When the client calls GET /v1/reporting/report-calculation/ with id from latest response
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value PENDING for property status

    Scenario: Pending ReportCalculation is processed within 10 seconds and returns error without Config:CONFIG_ENTITY
        Given document ReportConfig:1 is stored in database app
        Given document ReportCalculation:2 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:2
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value PENDING for property status
        Given emit ReportCalculationEvent for ReportCalculation:2 in tenant local-spring
        Then the client waits for 5000 milliseconds
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:2
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value FINISHED_ERROR for property status

    Scenario: Pending ReportCalculation is processed within 10 seconds
        Given document ReportConfig:1 is stored in database app
        Given document Config:CONFIG_ENTITY is stored in database app
        Given document ReportCalculation:2 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:2
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value PENDING for property status
        Given emit ReportCalculationEvent for ReportCalculation:2 in tenant local-spring
        Then the client waits for 5000 milliseconds
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:2
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value FINISHED_SUCCESS for property status
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:2/data
        Then the client receives an json object
        Then the client receives status code of 200

    Scenario: ReportCalculation with arguments is processed within 5 seconds
        Given document ReportConfig:3 is stored in database app
        Given document Config:CONFIG_ENTITY is stored in database app
        Given document ReportCalculation:3 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:3
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value PENDING for property status
        Given emit ReportCalculationEvent for ReportCalculation:3 in tenant local-spring
        Then the client waits for 5000 milliseconds
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:3
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value FINISHED_SUCCESS for property status
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:3/data
        Then the client receives an json object
        Then the client receives status code of 200

    Scenario: ReportCalculation for v2 ReportConfig is processed within 5 seconds
        Given document ReportConfig:4 is stored in database app
        Given document Config:CONFIG_ENTITY is stored in database app
        Given document ReportCalculation:4 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:4
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value PENDING for property status
        Given emit ReportCalculationEvent for ReportCalculation:4 in tenant local-spring
        Then the client waits for 5000 milliseconds
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:4
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value FINISHED_SUCCESS for property status
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:4/data
        Then the client receives an json object
        Then the client receives status code of 200

    Scenario: ReportCalculation for v1 ReportConfig with named arguments is processed within 5 seconds
        Given document ReportConfig:5 is stored in database app
        Given document Config:CONFIG_ENTITY is stored in database app
        Given document ReportCalculation:5 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:5
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value PENDING for property status
        Given emit ReportCalculationEvent for ReportCalculation:5 in tenant local-spring
        Then the client waits for 5000 milliseconds
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:5
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value FINISHED_SUCCESS for property status
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:5/data
        Then the client receives an json object
        Then the client receives status code of 200
