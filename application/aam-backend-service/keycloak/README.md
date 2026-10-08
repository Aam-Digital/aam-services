# Keycloak client definitions

The Keycloak clients that aam-services depends on, as files for deployment tooling to import. A release that needs
a new permission carries it here, and the integration tests import the same `aam-backend-client.json`.

The files are part of the Docker image: `/opt/app/keycloak/` holds the definitions that match the image version.
Read them from the image you deploy, not from a branch. Which realm a client goes into, and when, stays a
deployment decision; the frontend `app` client and the realm's shared configuration are out of scope.

| File | Client | Goes into | Used by |
|---|---|---|---|
| [`aam-backend-client.json`](aam-backend-client.json) | `aam-backend` | the instance realm | aam-services (Keycloak admin access), replication-backend (user and role lookup) |
| [`carbone-render-client.json`](carbone-render-client.json) | `carbone-<INSTANCE_NAME>` | the shared platform realm | the export module (token for the oauth2-proxy in front of Carbone) |

Both are confidential service-account clients without interactive login. Each file is a realm representation with
the `clients` entry and the `users` entry of its service account (Keycloak's partial import does not create that
user itself).

## Variables

Deployment-specific values are keycloak-config-cli placeholders, `$(env:NAME)`. The names are a contract. The
files never contain a secret; use secrets that need no JSON escaping, e.g. `openssl rand -hex 32`.
An import sets the client's secret to `AAM_BACKEND_CLIENT_SECRET`: for an existing client pass its current secret,
and generate a new one only when the client does not exist yet, or the running services lose access.

| Variable | File | Value |
|---|---|---|
| `AAM_BACKEND_REALM` | `aam-backend-client.json` | Name of the instance realm (the backend's `KEYCLOAK_REALM`). |
| `AAM_BACKEND_CLIENT_SECRET` | `aam-backend-client.json` | Client secret (the backend's `KEYCLOAK_CLIENTSECRET`, replication-backend's `KEYCLOAK_ADMIN_CLIENT_SECRET`). |
| `CARBONE_REALM` | `carbone-render-client.json` | Name of the shared platform realm. |
| `INSTANCE_NAME` | `carbone-render-client.json` | Name of the instance; the client id becomes `carbone-<INSTANCE_NAME>` (`aam-render-api-client-configuration.auth-config.client-id`). |
| `CARBONE_CLIENT_SECRET` | `carbone-render-client.json` | Client secret (`aam-render-api-client-configuration.auth-config.client-secret`). |
| `OAUTH2_PROXY_CLIENT_ID` | `carbone-render-client.json` | Client id of the oauth2-proxy in front of Carbone; the audience mapper adds it to the access token. That client must exist in the same realm. |

## `aam-backend` roles

The service account holds these `realm-management` roles:

| Role | Needed for |
|---|---|
| `manage-clients` | Creating the client scopes the API modules check (`reporting_read`, `reporting_write`, `third_party_authentication`) on startup and making the reporting scopes Default for API clients. |
| `manage-users` | Creating the Keycloak user of an external system on its first login (third-party-authentication). |
| `view-users` | Looking up users by id (notification recipients) and by email (third-party-authentication); replication-backend reads users and their realm roles. |

`manage-users` also allows reading users, but `view-users` stays because it is the documented role for reads.
`manage-realm` (granted by older deployment scripts) is used by no call and `query-users` is part of
`view-users`, so importing the file removes both from a service account that still has them.

## Getting the files

```shell
id=$(docker create ghcr.io/aam-digital/aam-services:<version>)
docker cp "$id:/opt/app/keycloak/." ./keycloak-definitions/
docker rm "$id"
```

This runs nothing from the image, so it does not depend on which tools the image contains.

To use them elsewhere, copy `/opt/app/keycloak/` out of the image, e.g. with an init container into a volume
shared with the import job. The files are readable by any user. For a local development stack, see
[Create the `aam-backend` client](../../../docs/developer/README.md#23-create-the-aam-backend-client).

## Importing

Tested with keycloak-config-cli 6.5.1 and Keycloak 26.7.

### keycloak-config-cli

```shell
docker run --rm -v "$PWD:/definitions:ro" \
  -e KEYCLOAK_URL=https://keycloak.example.org \
  -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=<admin password> \
  -e IMPORT_FILES_LOCATIONS=/definitions/aam-backend-client.json \
  -e IMPORT_VARSUBSTITUTION_ENABLED=true \
  -e IMPORT_MANAGED_CLIENT=no-delete \
  -e IMPORT_REMOTE_STATE_ENABLED=false \
  -e IMPORT_CACHE_ENABLED=false \
  -e AAM_BACKEND_REALM=<realm> -e AAM_BACKEND_CLIENT_SECRET=<secret> \
  adorsys/keycloak-config-cli:6.5.1-26
```

- `IMPORT_VARSUBSTITUTION_ENABLED=true` is required; a variable that is not set then fails the import.
- **`IMPORT_MANAGED_CLIENT=no-delete` is required.** By default, clients that an earlier import of the realm
  created and the current files do not declare are deleted, e.g. `aam-backend` by any other import, or the
  render clients of other instances in the shared realm.
- **`IMPORT_REMOTE_STATE_ENABLED=false` is required.** Otherwise each import stores the clients it declared in a
  realm attribute, and the next import of that realm in default managed mode deletes those it does not declare
  itself, e.g. `aam-backend` when the deployment's own keycloak-config-cli job for the realm's shared configuration
  runs next, or all `carbone-*` clients but the last imported in the shared realm.
- `IMPORT_CACHE_ENABLED=false`: otherwise an import is skipped when its file, after substitution, is unchanged
  since the last import into the realm, so a client deleted by hand is not recreated.
- **Check that the realm exists first** (`GET /admin/realms/<realm>` returns 200): keycloak-config-cli creates a
  realm it does not find, so a wrong name yields a new, almost empty realm instead of an error.
- The import makes the client and its service account match the file, including resetting hand-made changes.
  It removes the realm's default client scopes (`basic`, `profile`, ...) from the client; the token of a service
  account still has `sub`, so the services are not affected. The `service_account` scope that Keycloak adds
  itself stays.

### Keycloak partial import

Fill in the variables first, then post to the `partialImport` endpoint (or use Realm settings, Action,
Partial import in the admin console):

```shell
export AAM_BACKEND_REALM=<realm> AAM_BACKEND_CLIENT_SECRET=<secret>
perl -pe 's/\$\(env:(\w+)\)/$ENV{$1} \/\/ die "undefined variable $1\n"/ge' aam-backend-client.json \
  | jq '. + {ifResourceExists: "SKIP"}' \
  | curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$AAM_BACKEND_REALM/partialImport" \
      -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d @-
```

`SKIP` leaves an existing client as it is; `OVERWRITE` recreates it under a new internal id.

## Changing the files

The path `/opt/app/keycloak/` and the file and variable names above are a public contract; changing one is a
breaking change and goes into the release notes. Code that needs another permission for `aam-backend` adds the
role to `aam-backend-client.json` and to the role table, covers the call in
`KeycloakClientDefinitionsIntegrationTest`, and says so in the release notes.
