# Keycloak client definitions

The Keycloak clients that aam-services depends on, as files that deployment tooling imports instead of
keeping its own copy of their settings. What these clients need is decided by the code in this repository,
so a release that needs a new permission carries it here, and the integration tests import the same
`aam-backend-client.json` to prove that its permissions suffice.

The files are part of the Docker image: `/opt/app/keycloak/` always holds the definitions that match exactly
the version of the image that runs. Read them from the image you deploy, not from a branch.

Out of scope: the frontend `app` client and the realm's shared configuration. Which realm a client goes into,
and when it exists, stays a deployment decision.

| File | Client | Goes into | Used by |
|---|---|---|---|
| [`aam-backend-client.json`](aam-backend-client.json) | `aam-backend` | the instance realm | aam-services (Keycloak admin access) and replication-backend (user and role lookup) |
| [`carbone-render-client.json`](carbone-render-client.json) | `carbone-<INSTANCE_NAME>` | the shared platform realm | the export module (token for the oauth2-proxy in front of Carbone) |

Both are confidential service-account clients without interactive login flows. Each file is a Keycloak realm
representation holding the `clients` entry and the `users` entry of its service account. The user is part of
the file because Keycloak's partial import, unlike the admin API, does not create it for a client with service
accounts enabled.

## Variables

Everything that differs per deployment is a placeholder in keycloak-config-cli's variable syntax,
`$(env:NAME)`. The names are a contract with the deployments that fill them in.

| Variable | File | Value |
|---|---|---|
| `AAM_BACKEND_REALM` | `aam-backend-client.json` | Name of the instance realm, the backend's `KEYCLOAK_REALM`. |
| `AAM_BACKEND_CLIENT_SECRET` | `aam-backend-client.json` | Secret of the client, the backend's `KEYCLOAK_CLIENTSECRET` and replication-backend's `KEYCLOAK_ADMIN_CLIENT_SECRET`. |
| `CARBONE_REALM` | `carbone-render-client.json` | Name of the shared platform realm. |
| `INSTANCE_NAME` | `carbone-render-client.json` | Name of the instance; the client id becomes `carbone-<INSTANCE_NAME>`, the backend's `aam-render-api-client-configuration.auth-config.client-id`. |
| `CARBONE_CLIENT_SECRET` | `carbone-render-client.json` | Secret of the client, the backend's `aam-render-api-client-configuration.auth-config.client-secret`. |
| `OAUTH2_PROXY_CLIENT_ID` | `carbone-render-client.json` | Client id of the oauth2-proxy in front of Carbone. The audience mapper puts it into the access tokens of the render client, which the proxy requires. |

The files never contain a secret. Substitution is textual, so use secrets that need no escaping in a JSON
string, for example `openssl rand -hex 32`.

The `realm` property is read by keycloak-config-cli, which cannot import a file without it. Keycloak's partial
import ignores it.

## `aam-backend` roles

The service account of `aam-backend` holds these `realm-management` roles, each for a call that a service makes
with it:

| Role | Needed for |
|---|---|
| `manage-clients` | On startup, aam-services creates the client scopes its API modules check (`reporting_read`, `reporting_write`, `third_party_authentication`) and changes the reporting scopes from Optional to Default for the API clients that have them only as Optional. |
| `manage-users` | aam-services creates the Keycloak user of an external system on its first login (third-party-authentication). |
| `view-users` | aam-services looks up a user by id (email of notification recipients) and by email (third-party-authentication). replication-backend reads a user and its realm roles. `query-users` is part of this role. |

Keycloak also lets `manage-users` read users, so `view-users` makes no difference in effect today. It stays
because it is the role that Keycloak documents for reading users, and the calls that only read should not
depend on the permission to change users.

`manage-realm`, which older deployment scripts also granted, is used by no call of either service and does not
open any of the user endpoints above, so it is not part of the definition. Importing the file with
keycloak-config-cli removes it, and `query-users`, from a service account that still has them.

## Getting the files

```shell
docker run --rm --entrypoint cat ghcr.io/aam-digital/aam-services:<version> /opt/app/keycloak/aam-backend-client.json
```

Where the image is not run directly, copy the folder out of it, for example with an init container of the
instance's own image that copies `/opt/app/keycloak/` into a volume shared with the import job.
The files are readable by any user, since the image can run as non-root.

For a local development stack, see [Create the `aam-backend` client](../../../docs/developer/README.md#23-create-the-aam-backend-client).

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
  -e AAM_BACKEND_REALM=<realm> -e AAM_BACKEND_CLIENT_SECRET=<secret> \
  adorsys/keycloak-config-cli:latest-26
```

- Variable substitution is off by default. With it on, a variable that is not set fails the import.
- **Set `IMPORT_MANAGED_CLIENT=no-delete`.** By default keycloak-config-cli deletes the clients that an earlier
  import of the realm created and that the files of the current import no longer declare. Importing any other
  file for the same realm afterwards, even in the same run, would delete `aam-backend`. Every instance imports
  its own render client into the shared platform realm, so without it each import would delete the render
  clients of the other instances.
- The import makes the client and its service account match the file: it also resets declared settings that
  were changed in Keycloak by hand, and removes roles and default client scopes that the file does not list.
- A run is skipped for a file that has not changed since its last import, so a client deleted by hand is not
  recreated until then. `IMPORT_CACHE_ENABLED=false` turns this off.

### Keycloak partial import

Partial import (admin console: Realm settings, Action, Partial import; or the `partialImport` admin endpoint)
creates the client and its service account. It does not substitute variables, so fill them in first:

```shell
export AAM_BACKEND_REALM=<realm> AAM_BACKEND_CLIENT_SECRET=<secret>
perl -pe 's/\$\(env:(\w+)\)/$ENV{$1} \/\/ die "undefined variable $1\n"/ge' aam-backend-client.json \
  | jq '. + {ifResourceExists: "SKIP"}' \
  | curl -sf -X POST "$KEYCLOAK_URL/admin/realms/$AAM_BACKEND_REALM/partialImport" \
      -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d @-
```

An existing client is left as it is with `SKIP`. `OVERWRITE` recreates the client under a new internal id, so a
deployment that has to change an existing client needs its own repair path.

### Carbone render client

Import `carbone-render-client.json` the same way, with its own variables. The audience mapper only adds the
oauth2-proxy to the `aud` claim of the tokens if a client with the id `OAUTH2_PROXY_CLIENT_ID` exists in the
same realm, so create that client first.

## Changing the files

The path `/opt/app/keycloak/` and the file and variable names above are a public contract: changing one is a
breaking change for the deployments and goes into the release notes. A release that needs another permission
for `aam-backend` adds the role to `aam-backend-client.json` and says so in the release notes.
