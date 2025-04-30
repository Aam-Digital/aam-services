# Third-Party-Authentication Module (implementation)
_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/third-party-authentication.md)_

## Use Case / Flow
This backend module acts in tandem with the [Keycloak Third-Party Authentication provider](../../../../../../../../keycloak-third-party-authentication/README.md) plugin.

The module here
1. handles API requests from an external "master" system,
2. issues a special token to allow login without entering normal credentials manually in our Keycloak and
3. when Keycloak receives the token validates it (and then blocks it against repeated use)


## Development Setup
Use the developer setup [docs/developer](../../../../../../../../../docs/developer/README.md) to run required DB and Keycloak instances.

Follow the setup steps described in the [module README](../../../../../../../../../docs/modules/third-party-authentication.md).

Set `AAMKEYCLOAKCONFIG_CLIENTSECRET` in your local env variables to the secret of the client you created in Keycloak.
