@ThirdPartyAuthentication
Feature: Third-party authentication SSO session API

    # These scenarios pin the behaviour of the two distinct lifetimes that a session carries:
    # the one-time login ticket (sessionToken, valid for minutes) and the redirect binding
    # (sessionId -> redirectUrl), which ndb-core keeps in localStorage and reads indefinitely.

    Background:
        Given all default databases are created

    Scenario: Starting a session without authentication returns 401
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 401

    Scenario: Starting a session for a known account returns a session id and token
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Then the client receives a non-empty value for property sessionId
        Then the client receives a non-empty value for property sessionToken
        Then the client receives a non-empty value for property entryPointUrl
        Then the client receives a non-empty value for property validUntil

    Scenario: Starting a session for an unknown account creates the account and a user document
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account does not exist yet
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Then a new account is created in the authentication system
        Then database app contains 1 document

    Scenario: Verifying a session returns the linked user
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and session token
        Then the client receives status code of 200
        Then the client receives the signed-in user id for property userId

    Scenario: Verifying a session a second time is rejected
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and session token
        Then the client receives status code of 200
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and session token
        Then the client receives status code of 400
        Then the client receives value SESSION_ALREADY_USED for property errorCode

    Scenario: Verifying a session with the wrong token is rejected
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and session token not-the-issued-token
        Then the client receives status code of 400
        Then the client receives value INVALID_SESSION_TOKEN for property errorCode

    Scenario: Verifying an unknown session is rejected
        When the client calls GET /v1/third-party-authentication/session/unknown-session-id?session_token=some-token
        Then the client receives status code of 400
        Then the client receives value INVALID_SESSION for property errorCode

    Scenario: Fetching the redirect url for a session
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and suffix /redirect
        Then the client receives status code of 200
        Then the client receives value https://external-system.example.com/case/42 for property redirectUrl

    # The redirect binding deliberately outlives the login ticket: ndb-core's "go to the external
    # system" button calls this endpoint long after the session was redeemed.
    Scenario: The redirect url stays available after the session was already used
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and session token
        Then the client receives status code of 200
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and suffix /redirect
        Then the client receives status code of 200
        Then the client receives value https://external-system.example.com/case/42 for property redirectUrl

    Scenario: Fetching a redirect url without authentication returns 401
        When the client calls GET /v1/third-party-authentication/session/any-session-id/redirect
        Then the client receives status code of 401

    Scenario: Fetching the redirect url of another user's session is rejected
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists for another user
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_1
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and suffix /redirect
        Then the client receives status code of 400
        Then the client receives value INVALID_USER_SESSION for property errorCode

    Scenario: Fetching the redirect url of an unknown session is rejected
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        When the client calls GET /v1/third-party-authentication/session/unknown-session-id/redirect
        Then the client receives status code of 400
        Then the client receives value INVALID_SESSION for property errorCode

    Scenario: A session started without a redirect url has no redirect
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_2
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and suffix /redirect
        Then the client receives status code of 400
        Then the client receives value NO_REDIRECT_FOUND for property errorCode

    # Legacy fallback for the Tola integration, which does not send redirectUrl yet.
    Scenario: A blank redirect url falls back to the tola_frontend_url in additionalData
        Given signed in as client dummy-client with secret client-secret in realm dummy-realm
        And the external user account already exists
        When the client calls POST /v1/third-party-authentication/session with body UserSessionRequest_3
        Then the client receives status code of 200
        Given the client stores the session from the latest response
        When the client calls GET /v1/third-party-authentication/session/ with stored session id and suffix /redirect
        Then the client receives status code of 200
        Then the client receives value https://tola.example.com/dashboard for property redirectUrl
