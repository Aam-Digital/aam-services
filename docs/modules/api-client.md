# Aam Digital - API Clients (self-service API access)

Lets the account managers of an Aam Digital system create the credentials ("API clients") that external systems
use to access the APIs of this backend (e.g. the [Reporting API](reporting.md#api-access-to-reports) or
[Third-Party-Authentication](third-party-authentication.md)), without access to the Keycloak admin console.

In the Aam Digital app, this is the "API Access" page in the admin section (User Management),
available to users with the `account_manager` role.

An API client is a Keycloak client of the realm that:

- is confidential and can only use the OAuth2 client credentials flow (no browser login, no redirect URIs),
- has exactly the client scopes selected when creating it, as "Default" client scopes
  (and no "Optional" client scopes, so that it cannot request any other access),
- has the client id `api-<name>-<random suffix>`.

Its secret is only returned when the client is created or its secret is regenerated.

-----

## Access control

- Only users of the frontend app (tokens issued to the `app` Keycloak client) with the realm role `account_manager`
  can use the endpoints. API clients never can, whatever roles their service account has.
- The client scopes offered are those of the enabled feature modules (their `ClientScopeRequest`s), see `GET /scope`.
- Giving the `third_party_authentication` client scope additionally requires the realm role `admin_app`,
  because a client with this scope can sign in to the app as any user.
- Only clients created through this module (marked with the client attribute `aam.api-client.managed`
  and the client id prefix `api-`) are listed, changed or deleted.
  Clients set up in the Keycloak admin console are not affected, even if asked for by their client id.

### API Specification
_see [api-specs/api-client-api](../api-specs/api-client-api-v1.yaml)_

-----

## Setup

The module is enabled by default (`FEATURES_APICLIENT_ENABLED=true`), but only active if the backend has
admin access to Keycloak (the `KEYCLOAK_*` environment variables,
see [third-party-authentication setup](third-party-authentication.md#setup)).
The service account of the backend's Keycloak client (`aam-backend`) needs the `realm-management` role
`manage-clients`, which the [shipped client definition](../../application/aam-backend-service/keycloak/aam-backend-client.json)
includes.

```
> GET /actuator/features

{
  "api-client": { "enabled": true }
}
```

For third-party-authentication (single sign-on), an API client is not enough:
the realm also needs the authentication flow described in
[third-party-authentication](third-party-authentication.md#create-an-authentication-flow), which is set up once per realm.
