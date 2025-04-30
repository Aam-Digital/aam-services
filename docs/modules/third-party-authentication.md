# Aam Digital - Third-Party Authentication / "Single-Sign-On" Integration
Allow another platform's users to log into Aam Digital with their (external) user accounts.
This enables a deeper integration with other systems like TolaData, as users only have to maintain one account.

## Overview
This flow enables other trusted authentication systems to sign-in users on their own.
For example:

- **System A:** Aam Digital system with a dedicated user database and authentication solution.
- **System B:** Other platform which is a partner for your system but also has a user database and an authentication solution.

1. User U1 from System B now wants to switch into your System A, but without sign-up or sign-in again. System B already identified the user U1.
2. System B creates a session on System A for the user U1 in the background (making an API request to the module described here).
3. User U1 is forwarded to the System A with a valid session identifier as payload.
4. The authentication solution from System A is validating the session information.
5. If necessary, System A automatically creates a new User U1 in the user database
6. System A signs in the user U1. The user does not have to enter a password on System A.

### Dependencies
The Keycloak server requires a special Authenticator: [see keycloak-third-party-authentication](/application/keycloak-third-party-authentication/README.md)

### API Specification
_see [api-specs/third-party-authentication-api](../api-specs/third-party-authentication-api-v1.yaml)_


## Setup
The backend module requires environment variables to access the Keycloak server:
```dotenv
FEATURES_THIRDPARTYAUTHENTICATION_ENABLED=true

AAMKEYCLOAKCONFIG_REALM=master
AAMKEYCLOAKCONFIG_CLIENTID=aam-integration
AAMKEYCLOAKCONFIG_CLIENTSECRET=1234
AAMKEYCLOAKCONFIG_SERVERURL=http://localhost:8888
AAMKEYCLOAKCONFIG_APPLICATIONURL=aam.localhost
```

### Keycloak configuration
You need to configure your Keycloak Realm to support the third-party-api.
Check out the provider: [application/keycloak-third-party-authentication](../application/keycloak-third-party-authentication/)_

#### Enable the third-party-authentication provider in Keycloak
The custom Keycloak Provider is already enabled in the default aam-keycloak image.

You should create a Keycloak Client in the Realm that is used by the external system to authenticate itself against our API.

Additionally, a Keycloak User Role "third-party-authentication-provider" is required.
This has to be assigned to the Keycloak Client as a "Service Account Role".

#### Create an Authentication Flow

1. Go to the `Authentication` settings in your Realm and copy the default `browser` flow and name it `browser-sso`.
![dev-2.png](../assets/third-party-authentication/dev-2.png)

2. Click on `Add step` and select `Aam Digital - Third Party Authenticator`. It will be placed at the end of the list.
![dev-3.png](../assets/third-party-authentication/dev-3.png)

3. Now move the `Aam Digital - Third Party Authenticator` block between `Cookie` and `Kerberos`.
4. Set the `Requirement` to `Alternative`
5. Click on the settings icon on the right to configure:
   - **Alias** to any name of the API / system
   - **API-Base Url** to the API module URL of the aam-services backend, e.g. `https://my-realm.aam.digital.app/api/v1`
![dev-4.png](../assets/third-party-authentication/dev-4.png)

6. Switch to: `Clients` -> `app` -> `Advanced` and scroll down to `Authentication flow overrides`
   - Select the `browser-sso` flow here:
![dev-5.png](../assets/third-party-authentication/dev-5.png)

Done. The Third-Party-Authentication from an external system should now be usable.
