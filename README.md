# Aam Digital Services (aam-backend-service)

Collection of aam-digital services and tools

[![Maintainability](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/maintainability)](https://codeclimate.com/github/Aam-Digital/aam-services/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/test_coverage)](https://codeclimate.com/github/Aam-Digital/aam-services/test_coverage)

A modularize Spring Boot application that contains API modules for [Aam Digital's case management platform](https://github.com/Aam-Digital/ndb-core).

## Setup

See ndb-setup for instructions to enable the backend in an overall system: [ndb-setup README](https://github.com/Aam-Digital/ndb-setup?tab=readme-ov-file#api-integrations-and-sql-reports)

The individual modules like "Reporting" require some setup and environment variables. Please refer to the respective READMEs below for each API Module.

## API Modules

- **[Reporting](./docs/modules/reporting.md)**: Calculate aggregated reports and run queries on all data, accessible for external services for API integrations of systems
- **[Export](./docs/modules/export.md)**: Template based file export API. Uses [carbone.io](https://carbone.io) as templating engine.
