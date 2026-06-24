@Notification
Feature: Notification admin API

    Background:
        Given all default databases are created

    Scenario: Send a device-test message returns the create outcome
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/notification/message/device-test without body
        Then the client receives an json object
        Then the client receives status code of 200
