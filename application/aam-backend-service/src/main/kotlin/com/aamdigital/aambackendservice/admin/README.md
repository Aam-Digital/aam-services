# Admin Module (implementation)
_for details about setup & usage of this module [see README in docs folder](../../../../../../../../docs/modules/admin.md)_

The module is always enabled (`AdminFeatureInfoEndpoint`). Every endpoint requires `RequiresAdminAccess`.
Parts that depend on other systems live in their own sub-package and report their status as a sub-feature,
like `apiclient` (`admin.api-client`, only active with Keycloak admin access: `ConditionalOnKeycloakAdminAccess`).

## API clients (`apiclient`)
Administrators of the app create, list, delete and regenerate the secret of Keycloak clients for external systems
(`ApiClientController`). The backend does this with its own Keycloak admin access (`manage-clients`),
so the users of the app need no Keycloak admin role for it.

- `KeycloakApiClientAdministration` talks to the Keycloak Admin REST API. It is the security boundary:
  any client without the `aam.api-client.managed` attribute and the `api-` client id prefix is treated as if it
  did not exist, because `manage-clients` would allow changing every client of the realm (including `app` and
  `aam-backend`).
- `DefaultCreateApiClientUseCase` validates the request. The client scopes offered are those of the enabled
  feature modules (`ClientScopeRequest` beans).

## Access Control
`RequiresAdminAccess`: the `FRONTEND_CLIENT` authority (token issued to the app's Keycloak client)
and the realm roles `account_manager` and `admin_app`. `FRONTEND_CLIENT` is required, so that an API client whose
service account happens to have these roles cannot administer the system (e.g. create further API clients).

## Testing
`KeycloakApiClientAdministrationIntegrationTest` runs against a real Keycloak, signed in with the `aam-backend`
client definition that ships with the application, so it also proves that its permissions suffice.
