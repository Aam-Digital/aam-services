# Third-Party-Authentication Module (implementation)
_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/third-party-authentication.md)_

## Use Case / Flow
This backend module acts in tandem with the [Keycloak Third-Party Authentication provider](../../../../../../../../keycloak-third-party-authentication/README.md) plugin.

The module here
1. handles API requests from an external "master" system,
2. issues a special token to allow login without entering normal credentials manually in our Keycloak and
3. when Keycloak receives the token validates it (and then blocks it against repeated use)

## Access Control
`POST /session` requires the `third_party_authentication` client scope in the access token
(`SCOPE_third_party_authentication` authority, see `ThirdPartyAuthenticationScopes`).
The module registers a `ClientScopeRequest` (common `keycloak` package), so the scope is created on startup.
Assigning it to an API client is up to the Keycloak administrator.


## Development Setup
Use the developer setup [docs/developer](../../../../../../../../../docs/developer/README.md) to run required DB and Keycloak instances.

Follow the setup steps described in the [module README](../../../../../../../../../docs/modules/third-party-authentication.md).

Set `KEYCLOAK_CLIENTSECRET` in your local env variables to the secret of the client you created in Keycloak.
