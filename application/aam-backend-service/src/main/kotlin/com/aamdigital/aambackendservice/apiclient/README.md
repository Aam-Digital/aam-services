# API Client Module (implementation)
_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/api-client.md)_

## Use Case / Flow
Account managers of the app create, list, delete and regenerate the secret of Keycloak clients for external systems
(`ApiClientController`). The backend does this with its own Keycloak admin access (`manage-clients`),
so the users of the app need no Keycloak admin role for it.

- `KeycloakApiClientAdministration` talks to the Keycloak Admin REST API. It is the security boundary:
  any client without the `aam.api-client.managed` attribute and the `api-` client id prefix is treated as if it
  did not exist, because `manage-clients` would allow changing every client of the realm (including `app` and
  `aam-backend`).
- `DefaultCreateApiClientUseCase` validates the request and decides which client scopes the user may give:
  those of the enabled feature modules (`ClientScopeRequest` beans), each restricted to users with its
  `grantRequiresRole`, if set.

## Access Control
`RequiresApiClientAdministrationAccess`: the `FRONTEND_CLIENT` authority (token issued to the app's Keycloak client)
and the realm role `account_manager`. Both are required, so that an API client whose service account happens to
have that role cannot create further API clients.

## Testing
`KeycloakApiClientAdministrationIntegrationTest` runs against a real Keycloak, signed in with the `aam-backend`
client definition that ships with the application, so it also proves that its permissions suffice.
