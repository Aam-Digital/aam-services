Feature: the export endpoint handles template creation

    Scenario: client makes call to GET /export/template and receives an template id
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/template with file pdf-test-file-1.pdf
        Then the client receives an json object
        Then the client receives status code of 200
