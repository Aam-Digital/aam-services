# Aam Digital - Admin API

Administration of an Aam Digital system by its administrators, through the admin section of the Aam Digital app.
The module is always enabled; parts that depend on other systems report their status as sub-features of `admin`
in `/actuator/features`.

Only users of the frontend app (tokens issued to the `app` Keycloak client) with both realm roles `account_manager`
and `admin_app` can use it. API clients never can, whatever roles their service account has.

### API Specification
_see [api-specs/admin-api](../api-specs/admin-api-v1.yaml)_

-----

## API clients (self-service API access)

Administrators create the credentials ("API clients") that external systems use for the APIs of this backend
(e.g. the [Reporting API](reporting.md#api-access-to-reports) or [Third-Party-Authentication](third-party-authentication.md)),
in the app's "API Access" page (User Management). The page shows the credentials together with the token URL and the
API base URL, to pass on to the external system.

An API client is a Keycloak client of the realm that
- is confidential and can only use the OAuth2 client credentials flow (no browser login, no redirect URIs),
- has exactly the selected client scopes, as "Default" scopes and without any "Optional" scopes, so that it cannot
  request any other access (the scopes offered are those of the enabled feature modules),
- has the client id chosen when creating it, which has to start with `api-`, or else `api-<name>-<random suffix>`,
- gets no roles in its access tokens ("Full scope allowed" off), so its access is decided by its client scopes only,
  never by the realm roles that Keycloak gives its service account (e.g. the realm's default roles).

Its secret is only shown when the client is created or its secret is regenerated.
Its client scopes can be changed later; access tokens issued before keep their previous client scopes until they
expire.

**Audit trail:** creating an API client, changing its client scopes, regenerating its secret and deleting it (and every failed attempt) is logged
at `INFO` with the user who did it (email and Keycloak user id), as lines starting with `[audit]`.
Third-party-authentication also logs every session it creates with the API client and the user it is for: an API
client with the `third_party_authentication` scope can sign in as any user of the realm, so give it only to systems
that are trusted to do so.
Only clients created this way (client attribute `aam.api-client.managed` and the client id prefix `api-`) are listed,
changed or deleted; other clients of the realm are never affected.

**Setup:** only available if the backend has admin access to Keycloak (`KEYCLOAK_*`, see
[third-party-authentication setup](third-party-authentication.md#setup)) with the `realm-management` role
`manage-clients`, which the [`aam-backend` client definition](../../application/aam-backend-service/keycloak/README.md)
grants. Then `/actuator/features` reports:

```
{ "admin": { "enabled": true, "api-client": { "enabled": true } } }
```
