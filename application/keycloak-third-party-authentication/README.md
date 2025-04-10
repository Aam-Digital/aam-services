# keycloak-third-party-authentication

This provider extension for keycloak is alternative sign-in flow to enable third party authentication.

## Overview

The target of this flow is, to enable other trusted authentication systems to sign-in users on their own.
For example:

System A: Your System with a dedicated user database and authentication solution.
System B: Customer Platform wich is a partner for your platform, but also have a user database and a authentication
solution.

A User U1 from System B now wants to switch into your System A, but without sign-up or sign-in again. System B already
identified the user U1 and creates a session on System A for the user U1 in the background.
The User U1 is forwarded to the System A with a valid session identifier as payload. The authentication solution from
System A is validating the session information and automatically creates the User U1 in the user database and sign-in
the user U1.

## Development

As development setup, you can use the provided [docker-compose.yml](./docker-compose.yml).

### Enable debugging

To debug into the keycloak JVM, you need to enable the debug options in the [docker-compose.yml](./docker-compose.yml):

```yaml
name: keycloak-third-party-authentication
services:
  keycloak:
    environment:
      DEBUG: 'true'
      DEBUG_SUSPEND: 'y'
      DEBUG_PORT: '*:5005'
```

After that, you can start the containers `docker compose up -d`. You will see, that the keycloak container is waiting
for a
debugger to attach:

![docs-debug-1.png](assets/docs-debug-1.png)

Now create a Remote JVM Debug configuration in your IntelliJ:

![docs-debug-2.png](assets/docs-debug-2.png)

... start the configuration in debug mode. You will see, that the container is logging the connection now:

![docs-debug-3.png](assets/docs-debug-3.png)

**Attention**: sometimes, you need to start the debug configuration twice.

Done. Breakpoints should work as usual now.
