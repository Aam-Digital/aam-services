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

Both run together with `./gradlew test` — Docker must be available for the e2e tests.

### Running a specific Cucumber scenario

You can run individual Cucumber scenarios using environment variables to filter by tag, feature file, or scenario name:

```shell
# By tag (add e.g. @Focus to the scenario in the .feature file)
CUCUMBER_FILTER_TAGS="@Focus" ./gradlew test --tests "*CucumberTestRunner"

# By feature file path
CUCUMBER_FILTER_TAGS="@Notification" ./gradlew test --tests "*CucumberTestRunner"

# By scenario name (regex)
CUCUMBER_FILTER_NAME="client makes call to start a report calculation" ./gradlew test --tests "*CucumberTestRunner"

# By feature file path with line number for a single scenario
CUCUMBER_FEATURES="src/test/resources/cucumber/features/notification/notification-change-type.feature:8" ./gradlew test --tests "*CucumberTestRunner"
```

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

Modules (usually) have to be explicitly enabled through a feature flag configuration. For example,
`FEATURES_EXPORTAPI_ENABLED=true` in .env can toggle the feature flag

```
@ConditionalOnProperty(
    prefix = "features.export-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
```

## Message Queues

Most modules use RabbitMQ to decouple processing and allow for asynchronous processing of tasks.
Refer to the official documentation (the tutorials are quite good) if you are not familiar with the concept or the framework specifically.
