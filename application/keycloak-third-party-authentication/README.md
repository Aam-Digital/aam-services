# Keycloak Provider (Extension): keycloak-third-party-authentication

This provider extension for Keycloak is alternative sign-in flow to enable third party authentication.
To some extent this is based on [this guide](https://www.n-k.de/2023/03/keycloak-magic-login-link-passwordless.html). 

For an explanation of the feature overall, please refer to the [Module's README](../../docs/modules/third-party-authentication.md).

This is required as a counterpart to our API's [third-party-integration module](../aam-backend-service/src/main/kotlin/com/aamdigital/aambackendservice/thirdpartyauthentication/README.md).
It interacts with our API to authenticate a Keycloak user based on the session tokens managed by the aam-integration API.


## Build
The provider is built automatically by the CI (see `.github/workflows/`)
and published on GitHub.

You can also manually build the provider simply with docker using the following command:
```bash
docker build --output type=local,dest=./build .
## if needed in developer setup for testing, copy it:
sudo cp build/keycloak-third-party-authentication.jar ../../docs/developer/container-data/keycloak/providers/keycloak-third-party-authentication.jar
```


## Setup
The .jar file built here (e.g. through the CI) should be built into a Keycloak image to make it available.
We build our custom Keycloak image with necessary providers in [Aam-Digital/aam-cloud-infrastructure](https://github.com/Aam-Digital/aam-cloud-infrastructure/blob/main/application/aam-keycloak/Dockerfile).


## Development
As development setup, you can use the provided [docker-compose.yml](./docker-compose.yml).

> WARNING: connecting to a localhost API may fail with `Connection refused` error
> This problem will not occur on server deployments with valid certificates.

For general guidance refer to the Keycloak "Server Development" documentation: [Authentication SPI walk through](https://www.keycloak.org/docs/latest/server_development/index.html#_auth_spi_walkthrough).

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

After that, you can start the containers `docker compose up -d`.
You will see, that the keycloak container is waiting for a debugger to attach:

![docs-debug-1.png](assets/docs-debug-1.png)

Now create a Remote JVM Debug configuration in your IntelliJ:

![docs-debug-2.png](assets/docs-debug-2.png)

... start the configuration in debug mode. You will see, that the container is logging the connection now:

![docs-debug-3.png](assets/docs-debug-3.png)

**Attention**: sometimes, you need to start the debug configuration twice.

Done. Breakpoints should work as usual now.
