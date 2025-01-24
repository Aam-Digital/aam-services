# Aam Digital Services (aam-backend-service)

Collection of aam-digital services and tools

[![Maintainability](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/maintainability)](https://codeclimate.com/github/Aam-Digital/aam-services/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/57213b5887a579196d6d/test_coverage)](https://codeclimate.com/github/Aam-Digital/aam-services/test_coverage)

A modularize Spring Boot application that contains API modules for [Aam Digital's case management platform](https://github.com/Aam-Digital/ndb-core).

## Setup

1. Create additional databases in CouchDB: `report-calculation` and `notification-webhook` (used by the Reporting Module to store details)
2. Set up necessary environment variables (e.g. using an `application.env` file for docker compose):

- see [example .env](./docs/examples/application.env)
- CRYPTO_CONFIGURATION_SECRET: _a random secret used to encrypt data_

3. See ndb-setup for instructions to enable the backend in an overall system: [ndb-setup README](https://github.com/Aam-Digital/ndb-setup?tab=readme-ov-file#api-integrations-and-sql-reports)

## API Modules

- **[Reporting](./docs/modules/reporting.md)**: Calculate aggregated reports and run queries on all data, accessible for external services for API integrations of systems
- **[Export](./docs/modules/export.md)**: Template based file export API. Uses [carbone.io](https://carbone.io) as templating engine.

## Development
The developer README provides detailed instructions how to set up a local testing environment: [docs/developer/README.md](docs/developer/README.md)