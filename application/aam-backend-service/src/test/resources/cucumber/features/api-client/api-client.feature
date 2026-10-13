Feature: Self-service API clients, managed by the administrators of the app

    # Managing API clients requires both the account_manager and the admin_app role.
    # dummy-client's service account has both roles, but it is an API client:
    # the credentials of one integration must not be usable to create more of them.

    Scenario: Listing API clients without authentication returns 401
        When the client calls GET /v1/api-client
        Then the client receives status code of 401

    Scenario: An API client cannot manage API clients, whatever roles its service account has
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/api-client
        Then the client receives status code of 403

    Scenario: A user of the app without the account_manager and admin_app roles cannot manage API clients
        Given signed in as user app-user with password app-user-password through client app in realm dummy-realm
        When the client calls GET /v1/api-client
        Then the client receives status code of 403
        When the client calls POST /v1/api-client with body CreateApiClientRequest_reporting
        Then the client receives status code of 403

    Scenario: An account manager without the admin_app role cannot manage API clients
        Given signed in as user account-manager with password account-manager-password through client app in realm dummy-realm
        When the client calls GET /v1/api-client
        Then the client receives status code of 403
        When the client calls POST /v1/api-client with body CreateApiClientRequest_reporting
        Then the client receives status code of 403

    Scenario: An administrator without the account_manager role cannot manage API clients
        Given signed in as user app-admin with password app-admin-password through client app in realm dummy-realm
        When the client calls GET /v1/api-client/scope
        Then the client receives status code of 403
        When the client calls POST /v1/api-client with body CreateApiClientRequest_reporting
        Then the client receives status code of 403

    Scenario: An administrator who manages accounts sees the client scopes of the enabled feature modules
        Given signed in as user account-admin with password account-admin-password through client app in realm dummy-realm
        When the client calls GET /v1/api-client/scope
        Then the client receives status code of 200
        Then the client receives array with 3 elements

    Scenario: An administrator who manages accounts creates an API client, regenerates its secret and deletes it
        Given signed in as user account-admin with password account-admin-password through client app in realm dummy-realm
        When the client calls POST /v1/api-client with body CreateApiClientRequest_reporting
        Then the client receives status code of 201
        Then the client receives a non-empty value for property clientId
        Then the client receives a non-empty value for property clientSecret
        Given the client stores the clientId from latest response
        When the client calls GET /v1/api-client
        Then the client receives status code of 200
        Then the client receives a json array
        When the client calls POST /v1/api-client/ with stored id and suffix /secret
        Then the client receives status code of 200
        Then the client receives a non-empty value for property clientSecret
        When the client calls DELETE /v1/api-client/ with stored id
        Then the client receives status code of 204
        When the client calls DELETE /v1/api-client/ with stored id
        Then the client receives status code of 404
        Then the client receives value API_CLIENT_NOT_FOUND for property errorCode

    Scenario: An administrator who manages accounts cannot change or delete clients of the realm that are not API clients
        Given signed in as user account-admin with password account-admin-password through client app in realm dummy-realm
        When the client calls DELETE /v1/api-client/app
        Then the client receives status code of 404
        When the client calls POST /v1/api-client/aam-backend/secret without body
        Then the client receives status code of 404

    Scenario: An administrator who manages accounts can give the third_party_authentication client scope
        Given signed in as user account-admin with password account-admin-password through client app in realm dummy-realm
        When the client calls POST /v1/api-client with body CreateApiClientRequest_sso
        Then the client receives status code of 201
        Given the client stores the clientId from latest response
        When the client calls DELETE /v1/api-client/ with stored id
        Then the client receives status code of 204
