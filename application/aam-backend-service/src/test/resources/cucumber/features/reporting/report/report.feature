Feature: the report endpoint persist to database

    Scenario: client makes call to GET /reporting/report and receives empty list
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report and assumes an array response
        Then the client receives status code of 200
        Then the client receives list of values
        Then the client receives array with 0 elements
