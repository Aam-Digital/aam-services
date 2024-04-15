Feature: the report calculation data endpoint persist to database

    Scenario: client makes call to GET /reporting/report-calculation/ReportCalculation:1/data and receives data
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        Given document ReportCalculation:1 is stored in database report-calculation
        Given document ReportData:1 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:1/data
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value 22cfbb91fcbff5a2e0755cc26567a81078898dfe939d07c9745149f45863ca31 for property dataHash
        Then the client receives value ReportData:1 for property _id

    # ReportCalculation not available
    Scenario: client makes call to GET /reporting/report-calculation/ReportCalculation:42/data and receives NotFound
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:42/data
        Then the client receives an json object
        Then the client receives status code of 404

    # ReportData not available
    Scenario: client makes call to GET /reporting/report-calculation/ReportCalculation:42/data and receives NotFound 2
        Given database app is created
        Given database report-calculation is created
        Given document ReportCalculation:1 is stored in database report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:1/data
        Then the client receives an json object
        Then the client receives status code of 404
