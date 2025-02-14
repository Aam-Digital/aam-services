# Aam Digital Services (aam-backend-service)

Collection of aam-digital services and tools

[![Maintainability](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/maintainability)](https://codeclimate.com/github/Aam-Digital/aam-services/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/test_coverage)](https://codeclimate.com/github/Aam-Digital/aam-services/test_coverage)

A modularize Spring Boot application that contains API modules for [Aam Digital's case management platform](https://github.com/Aam-Digital/ndb-core).

## API Modules

- **[Reporting](./docs/modules/reporting.md)**: Calculate aggregated reports and run queries on all data, accessible for external services for API integrations of systems
- **[Export](./docs/modules/export.md)**: Template based file export API. Uses [carbone.io](https://carbone.io) as templating engine.
- **[Skill](./docs/modules/skill.md)**: Integration with external system (SkillLab) to link and pull data into Aam Digital.

_Modules have to be enabled via a feature flag in the environment config and may need additional environment variables (see module READMEs)._


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
To run the system locally including required additional services like databases refer to the [docs/developer/README.md](docs/developer/README.md).

This backend is developed as independent modules that share some common services (e.g. for database access).

## Configuration through environment variables
Environment variables can be defined in an .env file using "snake case" variable names (see [examples/application.env](/docs/examples/application.env)).
These are transformed automatically to be available in the code as nested properties

For example, `FEATURES_EXPORTAPI_ENABLED=true` in .env can toggle the feature flag
```
@ConditionalOnProperty(
    prefix = "features.export-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
```