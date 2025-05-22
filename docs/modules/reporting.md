# Aam Digital - Reporting / Query API

An API to calculate "reports" (e.g. statistical, summarized indicators) based on entities in the primary database of an
Aam Digital instance.

This service allows to run SQL queries on the database.
In particular, this service allows users with limited permissions to see reports of aggregated statistics across all
data (e.g. a supervisor could analyse reports without having access to possibly confidential details of participants or
notes).

-----

## Setup

_(the following steps are automatically handled by the interactive setup
script ([ndb-setup](https://github.com/Aam-Digital/ndb-setup)) also)_

1. Set up necessary environment variables (e.g. using an `application.env` file for docker compose under
   `config/aam-backend-service/application.env` from the root folder where the "docker-compose.yml" exists):
    - see [example .env](/templates/aam-backend-service/application.template.env)
    - CRYPTO_CONFIGURATION_SECRET: _a random secret used to encrypt data_
2. Enable the backend in the overall docker compose setup as described in the ndb-setup
   README [here](https://github.com/Aam-Digital/ndb-setup?tab=readme-ov-file#set-up-api-integration)
    - or, if it was already enabled, re-up the docker compose and confirm the new containers and environment are running
3. Create `ReportConfig:` entities to define specific reports
    - the API / backend reports only support the `"mode": "sql"`
    - for details on report definitions,
      see https://aam-digital.github.io/ndb-core/documentation/additional-documentation/how-to-guides/create-a-report.html
4. Make sure the users who are supposed to access the reports in the frontend have permission to view `ReportConfig`
   entities
5. Within the app, users can now execute sql-based reports and see calculated results (configuration for the view in
   Config:CONFIG_ENTITY `"view:report": {"component": "Reporting"}`)

## API access to reports

Reports and their results are available for external services through the given API
endpoints ([see OpenAPI specs](../api-specs/reporting-api-v1.yaml)). Endpoints require a valid JWT access token, which
can be fetched via OAuth2 client credential flow.

### Initial setup of an API integration

1. Create a Keycloak "Client" (--> admin has
   to [create new client grant in Keycloak](https://www.keycloak.org/docs/latest/server_admin/#_oidc_clients))
    1. check "Client authentication" toggle
    2. for "Authentication flow" only "Service accounts roles" needs to be checked
    3. in the Client section, edit the newly created client and add the reporting_read and reporting_write scopes in the "Client scopes" tab (these should be created by the default realm, otherwise manually create these two in the "Client scopes" section)
    4. from the "Credentials" tab of the client you can now copy the secret:
       ![Keycloak Client Setup](../assets/keycloak-client-setup.png)
2. For integration with TolaData:
    - In TolaData, navigate to Data Tables or User Profile and add Aam Digital credentials
    - Get the client_id and client_secret (from the "Credentials" tab of the client created in Keycloak)
    - also
      see [Support Guide: Integration with TolaData](https://chatwoot.help/hc/aam-digital/articles/1726341005-integration-with-tola_data)
      for details of the required URLs

----

## Access a reporting via API (after setup)

1. Get valid access token using your client secret:

```bash
curl -X "POST" "https://keycloak.aam-digital.net/realms/<your_realm>/protocol/openid-connect/token" \
     -H 'Content-Type: application/x-www-form-urlencoded; charset=utf-8' \
     --data-urlencode "client_id=<your_client_id>" \
     --data-urlencode "client_secret=<your_client_secret>" \
     --data-urlencode "grant_type=client_credentials" \
     --data-urlencode "scopes=reporting_read reporting_write"
```

Check API docs for the required "scopes".
This returns a JWT access token required to provided as Bearer Token for any request to the API endpoints. Sample token:

```json
{
  "access_token": "eyJhbGciOiJSUzI...",
  "expires_in": 300,
  "refresh_expires_in": 0,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "reporting_read reporting_write"
}
```

### Manually execute a report calculation
2. Request the all available reports: `GET /v1/reporting/reports` (see OpenAPI specs for details)
3. Trigger the calculation of a reports data: `POST /v1/reporting/report-calculation/report/<report-id>`
4. Get status of the report calculation: `GET /v1/reporting/report-calculation/<calculation-id>`
5. Once the status shows the calculation is completed, get the actual result data:
   `GET /v1/reporting/report-calculation/<calculation-id>/data`

## Subscribe to continuous changes of a report
1. Create an initial webhook (if not already registered): `POST /v1/reporting/webhook`
   - pass your details how to receive the callback upon events
2. Register for events of the selected report for your webhook: `POST /v1/reporting/webhook/{webhookId}/subscribe/report/{reportId}` 
3. _... when data in Aam Digital changes (and once initially directly after you subscribe to a report) ..._
4. You receive an event object sent to your webhook with the current report-calculation reference
   - this does not contain the actual data, but only the reportCalculationId of the result that is ready
5. Use the report-calculation-id in the event to fetch actual data:
   - get metadata like timestamp of the calculation: `GET /v1/reporting/report-calculation/<calculation-id>`
   - get the actual report data: `GET /v1/reporting/report-calculation/<calculation-id>/data`
