@Notification
Feature: Notification device registration API

    Background:
        Given all default databases are created

    Scenario: Register a device without authentication returns 401
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 401

    Scenario: Register a device with valid authentication
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 204

    Scenario: Registering the same device token twice returns 400
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 204
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 400

    Scenario: Fetch a registered device
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 204
        When the client calls GET /v1/notification/device/test-device-token-1
        Then the client receives status code of 200
        Then the client receives value test-device-token-1 for property deviceToken
        Then the client receives value Test Device for property deviceName

    Scenario: Fetch a non-existent device returns 404
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/notification/device/non-existent-token
        Then the client receives status code of 404

    Scenario: Delete a registered device
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 204
        When the client calls DELETE /v1/notification/device/test-device-token-1
        Then the client receives status code of 204

    Scenario: Fetch a device after deletion returns 404
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls POST /v1/notification/device with body DeviceRegistration_1
        Then the client receives status code of 204
        When the client calls DELETE /v1/notification/device/test-device-token-1
        Then the client receives status code of 204
        When the client calls GET /v1/notification/device/test-device-token-1
        Then the client receives status code of 404
