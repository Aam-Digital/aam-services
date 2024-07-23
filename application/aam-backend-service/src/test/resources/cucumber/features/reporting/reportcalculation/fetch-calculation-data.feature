Feature: the report calculation data endpoint persist to database

    Scenario: client makes call to GET /reporting/report-calculation/ReportCalculation:1/data and receives data
        Given database app is created
        Given database report-calculation is created
        Given document ReportConfig:1 is stored in database app
        Given document ReportCalculation:1 is stored in database report-calculation
        Given attachment ReportData:1 added to document ReportCalculation:1 in report-calculation
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/reporting/report-calculation/ReportCalculation:1/data
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value ReportCalculation:1_data.json for property id

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
