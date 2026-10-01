# Aam Digital - Skill Integration API

An API to fetch external profiles from another system (specifically a skill tagging platform)
and import certain properties from matched external records into our entities via the frontend.

-----

## Architecture

![Skill Module Overview](../assets/skill-module-overview.png)

## Setup

### Provide environment configuration for skill module

You can find the latest version of the needed configuration in the package `aambackendservice.skill.di`.
The classes with the `@ConfigurationProperties` defines the needed properties.

An example configuration would be:

#### Disable the Skill feature: (default behaviour)

```yaml
features:
  skill-api:
    mode: disabled
```

#### Enable Skill feature with connection to SkillLab

Here an example configuration for the SkillLab project `42`

```yaml
features:
  skill-api:
    enabled: true
    mode: skilllab

skilllab-api-client-configuration:
  base-path: https://skilllab.app/<some-api-path>/project/42
  api-key: this-is-a-secret
  project-id: 42
  response-timeout-in-seconds: 30 # (default value)

```

### Configure permissions in the authentication system (Keycloak)

Example for the realm: `dummy-realm`

#### Setup Realm roles

- Open the Keycloak user interface and navigate to the `dummy-realm`
- Go to `Realm roles`
- Create two new roles by clicking on `Create role`:
  - `skill_admin`
  - `skill_reader`
- assign role to the `User(s)` or `Group(s)` who should be able to access the external profiles data

#### Add roles mapper for clients

It's necessary to add the roles to the JWT token to verify the roles in the backend.

For that, add an `roles mapper` for each client that sends requests to the skill api.
This should usually be the `app` client

- Open the Keycloak user interface and navigate to the `dummy-realm`
- Go to `Clients`
- Open the `app` client
- Switch to tab `Client scopes`
- Add the pre-defined client scope `roles` with Assigned Type `default`

## Using the API

_see [api-specs/skill-api](../api-specs/skill-api-v1.yaml)_

### Check if feature is enabled

You can make a request to the API to check if a certain feature is currently enabled and available:

```
> GET /actuator/features

// response:
{
  "skill": { "enabled": true }
}
```

If the _aam-services backend_ is not deployed at all, such a request will usually return a HTTP 504 error.
You should also account for that possibility.

### Syncing profiles from SkillLab

Every 10 minutes the backend fetches the profiles that changed in SkillLab since the last sync and
stores them locally. The time of the last sync is kept per project. It advances with every sync
that could fetch from SkillLab, even when individual profiles failed to sync.

Known limitations (neither is retried automatically):

- **A profile that fails to sync is skipped until it changes again.** The failure is logged as a
  warning, but the next scheduled sync only asks for profiles changed since the last sync, so it
  will not pick up the skipped profile unless that profile is edited in SkillLab again.
- **At most 10,000 profiles are fetched per sync.** If more have changed, the rest are skipped the
  same way, and this is not logged.

To recover, an admin (role `skill_admin`) can trigger a sync manually:

```
# re-import everything
> POST /v1/skill/sync/{projectId}?syncMode=FULL

# re-import everything changed since a given time
> POST /v1/skill/sync/{projectId}?syncMode=DELTA&updatedFrom=2024-12-03T11:50:00.231Z
```

A full re-import is still subject to the 10,000 profile limit.

### Configuration in Frontend
Define an Entity attribute of dataType "external-profile" to integrate the API in the application for users.
Refer to [external-profile.datatype](https://github.com/Aam-Digital/ndb-core/blob/master/src/app/features/skill/external-profile.datatype.ts) for required config details.
