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
Assigning it to an API client is up to the Keycloak administrator, or to an administrator of the app through the `admin` module ("API Access").


## Keycloak login flow
On startup, `ThirdPartyAuthenticatorActivation` adds the third-party authenticator to the realm's login flow (directly
after the cookie step), points it to this backend's API (`<application.base-url>/api`) and switches it on, if the flow
does not have it yet. A step that is already there is left as it is (only a warning if it cannot work).
The login flow itself comes from the deployment; other steps are never removed or reordered, and Keycloak's built-in
flow is left alone (only a warning). While Keycloak cannot be reached, it tries again in the background.
This needs the `realm-management` role `manage-realm`.

Disabling the module does not remove the step; see
[Disabling the module](../../../../../../../../../docs/modules/third-party-authentication.md#disabling-the-module)
for how to remove it in Keycloak.

## Development Setup
Use the developer setup [docs/developer](../../../../../../../../../docs/developer/README.md) to run required DB and Keycloak instances.

Follow the setup steps described in the [module README](../../../../../../../../../docs/modules/third-party-authentication.md).

Set `KEYCLOAK_CLIENTSECRET` in your local env variables to the secret of the client you created in Keycloak.
