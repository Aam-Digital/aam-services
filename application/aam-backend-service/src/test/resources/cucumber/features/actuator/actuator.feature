Feature: the health endpoint can be retrieved

    Scenario: client makes call to GET /actuator/health
        When the client calls GET /actuator/health
        Then the client receives an json object
        Then the client receives status code of 200
        And the client receives value UP for property status
