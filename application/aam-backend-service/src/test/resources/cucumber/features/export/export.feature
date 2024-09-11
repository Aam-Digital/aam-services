Feature: the export endpoint handles template creation

    Scenario: client makes call to POST /export/template and receives an template id
        Given database app is created
        Given database report-calculation is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/template with file pdf-test-file-1.pdf
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value 042ca26f8ca2eb3df4a6ee4ad0dc1f509928f7e83af24c01fd44362a2cc5921f for property templateId

    Scenario: client makes call to POST /export/template without token and receives Unauthorized
        Given database app is created
        Given database report-calculation is created
        When the client calls POST /v1/export/template with file pdf-test-file-1.pdf
        Then the client receives an json object
        Then the client receives status code of 401

    Scenario: client makes call to POST /export/render/ExportTemplate:1 and receives an template id
        Given database app is created
        Given database report-calculation is created
        Given document ExportTemplate:1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/render/ExportTemplate:1 with body RenderRequest:1
        Then the client receives status code of 200
