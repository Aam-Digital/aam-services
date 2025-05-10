# Aam Digital Services (aam-backend-service)

Collection of aam-digital services and tools

[![Maintainability](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/maintainability)](https://codeclimate.com/github/Aam-Digital/aam-services/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/test_coverage)](https://codeclimate.com/github/Aam-Digital/aam-services/test_coverage)

A modularize Spring Boot application that contains API modules for [Aam Digital's case management platform](https://github.com/Aam-Digital/ndb-core).

## API Modules

- **[Reporting](./docs/modules/reporting.md)**: Calculate aggregated reports and run queries on all data, accessible for external services for API integrations of systems
- **[Export](./docs/modules/export.md)**: Template based file export API. Uses [carbone.io](https://carbone.io) as templating engine.
- **[Skill](./docs/modules/skill.md)**: Integration with external system (SkillLab) to link and pull data into Aam Digital.
- **[Notification](./docs/modules/notification.md)**: Push Notification system, triggering events based on custom rules for each user.
- **[Third-Party-Authentication](./docs/modules/third-party-authentication.md)**: "Single-Sign-On" integration with other platforms.

_Modules have to be enabled via a feature flag in the environment config and may need additional environment variables as described in their module docs._


-----
# Setup & Configuration
This API is part of the larger Aam Digital platform and usually deployed together with additional services via docker.
See the [ndb-setup repository](https://github.com/Aam-Digital/ndb-setup) for tools and guidance how to run this in a production environment.

For instructions to enable the backend in an overall system: [ndb-setup README](https://github.com/Aam-Digital/ndb-setup?tab=readme-ov-file#api-integrations-and-sql-reports)

The individual modules like "Reporting" require some setup and environment variables.
Please refer to the respective READMEs in the "API Modules" list above for instructions about each API Module.

To run the system locally for development, refer to the [docs/developer/README.md](docs/developer/README.md) and sample docker files there.
These allow you to run required additional services like databases and queues on your machine.


-----
# Development
The developer README provides detailed instructions how to set up a local testing environment: [docs/developer/README.md](docs/developer/README.md)

This backend is developed as independent modules that share some common services (e.g. for database access).

## Frameworks & Tools
- Spring + Kotlin
- Spring Boot ([see intro](https://docs.spring.io/spring-boot/reference/using/index.html))
- Gradle ([see intro](https://docs.gradle.org/current/userguide/getting_started_eng.html))
- RabbitMQ (AMQP) for message queues ([see Tutorial](https://www.rabbitmq.com/tutorials/tutorial-three-spring-amqp#))

## Configuration through environment variables
Using Spring Boot's  system
our configurable values are represented in the [application.yaml](application/aam-backend-service/src/main/resources/application.yaml).

We define environment variables to set / overwrite the configuration to real values for production or testing.
Variables in the yaml hierarchy can be set in env variables using "snake case" variable names
(refer to the documentation of Spring Boot's [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) docs).

### Feature Flags
Modules (usually) have to be explicitly enabled through a feature flag configuration. For example, `FEATURES_EXPORTAPI_ENABLED=true` in .env can toggle the feature flag
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
