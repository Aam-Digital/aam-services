Feature: the export endpoint handles template creation

    Scenario: client makes call to POST /export/template with pdf-test-file and receives an template id
        Given database app is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/template with file pdf-test-file-1.pdf
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value 042ca26f8ca2eb3df4a6ee4ad0dc1f509928f7e83af24c01fd44362a2cc5921f for property templateId

    Scenario: client makes call to POST /export/template with docx-test-file and receives an template id
        Given database app is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/template with file docx-test-file-1.docx
        Then the client receives an json object
        Then the client receives status code of 200
        Then the client receives value 9545b4b168c892e32367e499fa913ce85c738562b0fd3bfc3a4023204adcebf0 for property templateId

    Scenario: client makes call to GET /export/template/ExportTemplate:2 and receives an docx
        Given database app is created
        Given document ExportTemplate:2 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/export/template/ExportTemplate:2
        Then the client receives status code of 200
        Then the client receives value application/vnd.openxmlformats-officedocument.wordprocessingml.document for header Content-Type

    Scenario: client makes call to GET /export/template/ExportTemplate:not-existing and receives an 404 response
        Given database app is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/export/template/ExportTemplate:not-existing
        Then the client receives status code of 404

    Scenario: client makes call to POST /export/template without token and receives Unauthorized
        Given database app is created
        When the client calls POST /v1/export/template with file pdf-test-file-1.pdf
        Then the client receives an json object
        Then the client receives status code of 401

    Scenario: client makes call to POST /export/render/ExportTemplate:1 and receives an template id
        Given database app is created
        Given document ExportTemplate:1 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/render/ExportTemplate:1 with body RenderRequest:1
        Then the client receives status code of 200
        Then the client receives value application/pdf for header Content-Type

    Scenario: client makes call to POST /export/render/ExportTemplate:2 and receives an template id
        Given database app is created
        Given document ExportTemplate:2 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/render/ExportTemplate:2 with body RenderRequest:1
        Then the client receives status code of 200
        Then the client receives value application/pdf for header Content-Type

    Scenario: client makes call to POST /export/render/ExportTemplate:2 and receives an docx
        Given database app is created
        Given document ExportTemplate:2 is stored in database app
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/render/ExportTemplate:2 with body RenderRequest:2
        Then the client receives status code of 200
        Then the client receives value application/vnd.openxmlformats-officedocument.wordprocessingml.document for header Content-Type

    Scenario: client makes call to POST /export/render/ExportTemplate:not-existing and receives an 404
        Given database app is created
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/export/render/ExportTemplate:not-existing with body RenderRequest:2
        Then the client receives status code of 404
