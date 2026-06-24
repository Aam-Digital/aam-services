# Aam Digital Services (aam-backend-service)

Collection of aam-digital services and tools

[![Maintainability](https://codeclimate.com/github/Aam-Digital/aam-services/badges/gpa.svg)](https://codeclimate.com/github/Aam-Digital/aam-services)
[![Test Coverage](https://qlty.sh/badges/gh/Aam-Digital/aam-services/test_coverage.svg)](https://qlty.sh/gh/Aam-Digital/aam-services)

A modularize Spring Boot application that contains API modules for [Aam Digital's case management platform](https://github.com/Aam-Digital/ndb-core).

## API Modules

- **[Reporting](./docs/modules/reporting.md)**: Calculate aggregated reports and run queries on all data, accessible for external services for API integrations of systems
- **[Export](./docs/modules/export.md)**: Template based file export API. Uses [carbone.io](https://carbone.io) as templating engine.
- **[Skill](./docs/modules/skill.md)**: Integration with external system (SkillLab) to link and pull data into Aam Digital.
- **[Notification](./docs/modules/notification.md)**: Push Notification system, triggering events based on custom rules for each user.
- **[Third-Party-Authentication](./docs/modules/third-party-authentication.md)**: "Single-Sign-On" integration with other platforms.

_Modules have to be enabled via a feature flag in the environment config and
may need additional environment variables as described in their module docs._

### Checking availability of a feature module

You can make a request to the API to check if a certain feature is currently enabled and available:

```
> GET /actuator/features

// response:
{
  "reporting": { "enabled": true },
  "notification": { "enabled": true },
  "export": { "enabled": true },
  "skill": { "enabled": true },
  "third-party-authentication": { "enabled": true }
}
```

The response lists feature modules with their status ("enabled").
If the _aam-services backend_ is not deployed at all, such a request will usually return a HTTP 504 error.
You should also account for that possibility.

---

# Setup & Configuration

This API is part of the larger Aam Digital platform and usually deployed together with additional services via docker.
See the [ndb-setup repository](https://github.com/Aam-Digital/ndb-setup) for tools and guidance how to run this in a production environment.

For instructions to enable the backend in an overall system: [ndb-setup README](https://github.com/Aam-Digital/ndb-setup?tab=readme-ov-file#api-integrations-and-sql-reports)

The individual modules like "Reporting" require some setup and environment variables.
Please refer to the respective READMEs in the "API Modules" list above for instructions about each API Module.

To run the system locally for development, refer to the [docs/developer/README.md](docs/developer/README.md) and sample docker files there.
These allow you to run required additional services like databases and queues on your machine.

---

# Development

The developer README provides detailed instructions how to set up a local testing environment: [docs/developer/README.md](docs/developer/README.md)

This backend is developed as independent modules that share some common services (e.g. for database access).

## Frameworks & Tools

- Spring + Kotlin
- Spring Boot ([see intro](https://docs.spring.io/spring-boot/reference/using/index.html))
- Gradle ([see intro](https://docs.gradle.org/current/userguide/getting_started_eng.html))
- RabbitMQ (AMQP) for message queues ([see Tutorial](https://www.rabbitmq.com/tutorials/tutorial-three-spring-amqp#))

## Running Tests

All commands should be run from `application/aam-backend-service/`.

```shell
# Run all tests (unit + e2e)
./gradlew test

# Run tests with coverage report (CSV + XML in build/reports/jacoco/)
./gradlew jacocoTestReport

# Run a single test class
./gradlew test --tests "com.aamdigital.aambackendservice.export.usecase.DefaultCreateTemplateUseCaseTest"
```

The test suite includes:

- **Unit tests** (JUnit 5 + Mockito) for individual use cases and services
- **E2E / integration tests** (Cucumber BDD) that spin up real Docker containers via Testcontainers (Keycloak, CouchDB, PostgreSQL, RabbitMQ, Carbone, SQS) and test full API flows. Cucumber feature files are located in `src/test/resources/cucumber/features/`.

Both run together with `./gradlew test`.

### Running the e2e tests

**Prerequisites:** JDK 21 and a **running Docker daemon** — Testcontainers starts
and tears down all the containers itself, so no other local setup (no
docker-compose, no manual Keycloak) is needed.

Run **only** the e2e tests (skips the unit tests):

```shell
./gradlew test --tests "*CucumberTestRunner"
```

Booting the full container stack and Spring Boot takes **~1–2 minutes** before the
first scenario runs, regardless of how many scenarios you select.

> **Gradle caches passing tests.** If you re-run without changing any source, the
> `test` task reports `UP-TO-DATE` and nothing actually executes. Force a re-run
> by prefixing `clean`, e.g. `./gradlew clean test --tests "*CucumberTestRunner"`.

#### Running specific scenarios

Filter scenarios with environment variables (always keep `--tests "*CucumberTestRunner"`
so the unit tests stay skipped):

```shell
# By tag (add e.g. @Focus to the scenario in the .feature file)
CUCUMBER_FILTER_TAGS="@Focus" ./gradlew test --tests "*CucumberTestRunner"

# By an existing tag (e.g. @Notification)
CUCUMBER_FILTER_TAGS="@Notification" ./gradlew test --tests "*CucumberTestRunner"

# By scenario name (regex)
CUCUMBER_FILTER_NAME="client makes call to start a report calculation" ./gradlew test --tests "*CucumberTestRunner"

# By feature file path (optionally with :line for a single scenario)
CUCUMBER_FEATURES="src/test/resources/cucumber/features/notification/notification-change-type.feature:8" ./gradlew test --tests "*CucumberTestRunner"
```

#### OpenAPI contract enforcement

The e2e suite also validates the API against the hand-written OpenAPI specs in
[`docs/api-specs/`](docs/api-specs/), so the specs stay honest (see
`src/test/kotlin/.../e2e/contract/`). For every call, the request/response is
checked against the owning module's spec; at the end of the run, each strict
module is checked for **coverage** (every documented operation is exercised) and
**inventory** (every controller endpoint is documented).

Strictness is per module via a system property (default: `reporting,export`) —
unlisted modules run in report-only mode (mismatches are logged, the build stays
green). Override the default to widen, narrow, or disable enforcement:

```shell
# report-only for every module:
./gradlew test --tests "*CucumberTestRunner" -Dcontract.strict.modules=
# a custom strict set:
./gradlew test --tests "*CucumberTestRunner" -Dcontract.strict.modules=reporting,export
```

When you change a module's API, update its `docs/api-specs/<module>-api-v1.yaml`
spec (and `docs/modules/<module>.md`) in the same change.

**VS Code:** Install the [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) extension to discover and run tests from the Test Explorer sidebar.

## Running the Application Locally

**Prerequisites:** JDK 21 and a running local dev stack (databases, message queues, Keycloak).
See [docs/developer/README.md](docs/developer/README.md) for full environment setup instructions.

```shell
# From application/aam-backend-service/
./gradlew bootRun --args='--spring.profiles.active=local-development'
```

The application starts on port **9000** with context path `/` when using the `local-development` profile (i.e. `http://localhost:9000/`). Other profiles may configure a different context path (for example `/api`).

Make sure to copy the reverse-proxy certificate for HTTPS connections to Keycloak (see [developer README](docs/developer/README.md#link-certificate-to-aam-backend-service)).

**VS Code:** Install the [Spring Boot Dashboard](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-spring-boot-dashboard) extension, then set the environment variable `SPRING_PROFILES_ACTIVE=local-development` in your launch configuration.

## Folder structure

Each feature module is separated into its own package.
Additionally, there is a `common` package that contains shared code.
This includes services to work with the CouchDB, get a feed of DB changes and other common functionality.
The "common" package also holds base functionality for authentication and security.

## Configuration through environment variables

Using Spring Boot's system
our configurable values are represented in the [application.yaml](application/aam-backend-service/src/main/resources/application.yaml).

We define environment variables to set / overwrite the configuration to real values for production or testing.
Variables in the yaml hierarchy can be set in env variables using "snake case" variable names
(refer to the documentation of Spring
Boot's [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) docs).

### Feature Flags

Modules (usually) have to be explicitly enabled through a feature flag configuration.
For example, `FEATURES_EXPORTAPI_ENABLED=true` in .env toggles the export module.

Each module defines a dedicated meta-annotation at its package root that gates
all of its beans (controllers, `@Configuration` classes, `@Bean` factories, etc.) —
look for `ConditionalOn<Module>Enabled.kt` alongside the module's `FeatureInfoEndpoint`.
Use that annotation on any class or `@Bean` method that should only exist when the
module is enabled.

Some modules additionally expose a mode-specific meta-annotation (e.g. for picking
between alternative backends). Stack the mode annotation on top of the
`Enabled` annotation when a bean requires both:

```kotlin
@RestController
@ConditionalOn<Module>Enabled
@ConditionalOn<Module><Mode>Mode
class SomeController(...)
```

The exact flags and modes available for each module are documented in the
respective [API Module docs](#api-modules).

#### Adding a flag for a new feature module

1. Define the meta-annotation at the module package root (next to the module's
   `FeatureInfoEndpoint`), e.g.
   `application/aam-backend-service/src/main/kotlin/com/aamdigital/aambackendservice/<module>/ConditionalOn<Module>Enabled.kt`:

    ```kotlin
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    @ConditionalOnProperty(
        prefix = "features.<module>",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    annotation class ConditionalOn<Module>Enabled
    ```

2. Annotate every bean that should only exist when the module is enabled with the
   new annotation — `@RestController`, `@Component`, `@Configuration`, and any
   `@Bean` factory inside.
3. Register a `FeatureRegistrar` so the module appears in `/actuator/features`
   (see [ReportingFeatureInfoEndpoint](application/aam-backend-service/src/main/kotlin/com/aamdigital/aambackendservice/reporting/ReportingFeatureInfoEndpoint.kt) for the pattern).
4. If the module consumes `document.changes` events, add a nested
   `@ConditionalOn<Module>Enabled` class to `ChangesConfiguration.AnyChangeConsumerEnabled`
   so change-detection auto-activates when the module is enabled.
5. Document the flag and any required env vars in `docs/modules/<module>.md`.

## Message Queues

Most modules use RabbitMQ to decouple processing and allow for asynchronous processing of tasks.
Refer to the official documentation (the tutorials are quite good) if you are not familiar with the concept or the framework specifically.
