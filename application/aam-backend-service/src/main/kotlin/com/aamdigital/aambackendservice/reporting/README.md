# Reporting Module (implementation)
_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/reporting.md)_

## Use Case / Flow
This backend module uses CouchDB [Structured Query Service (SQS)](https://neighbourhood.ie/products-and-services/structured-query-server)
to execute SQL queries on the Aam Digital system's database.
Queries are defined in as `ReportConfig` entities in the CouchDB and triggered through API requests.
Results are persisted in a separate "report-calculation" CouchDB and returned through API requests.

Processing is asynchronous and decoupled using RabbitMQ messages.

```mermaid
flowchart TD
    subgraph trigger[external triggers]
        calculationRequest>"POST /report-calculation/report/{reportId}"]
        externalDocChange>"CouchDB app doc changed"]
    end

    externalDocChange -.-> Q_DocChanges
    Q_DocChanges[[Queue: document.changes.report]] -.-> ChangeEventConsumer
    ChangeEventConsumer(ReportDocumentChangeEventConsumer) --> CreateCalculation
    calculationRequest --> CreateCalculation

    CreateCalculation[CreateReportCalculationUseCase] -.-> Q_Calculation
    Q_Calculation[[Queue: report.calculation]] -.-> CalculationListener
    CalculationListener(ReportCalculationEventListener) --> Calculation
    Calculation[ReportCalculationUseCase]
    style Calculation fill:#00C853

    CalculationListener -- if FINISHED_SUCCESS --> Q_Completed
    Q_Completed[[Queue: report.calculation.completed]] -.-> CompletedConsumer
    CompletedConsumer(ReportCalculationCompletedEventConsumer) --> CalculationChange
    CalculationChange[ReportCalculationChangeUseCase] -- if result changed --> WebhookNotification
    WebhookNotification["NotificationService (call Webhooks)"]
```

## Caches on the automatic change-detection path

`ReportDocumentChangeEventConsumer` runs for every changed document in the `app` database (up to
`CHANGES_LIMIT = 100` per poll tick), so nothing on that path may do per-change CouchDB I/O.
Two caches keep it in memory:

- **`ReportConfigCache`** — `reportId -> affected entity types`, i.e. the result of
  `SimpleReportQueryAnalyser`'s SQL regex, computed once per report definition instead of once per
  document change. Marked stale by `DefaultIdentifyAffectedReportsUseCase` whenever a
  `ReportConfig:*` change arrives, which is the only thing that can change it: `ReportConfig`
  documents live in `app`, the one database in `database-change-detection.included-databases`.
- **`WebhookSubscriptionCache`** — the set of report ids that any webhook is subscribed to. It
  reads `WebhookEntity` documents directly, so it never decrypts a webhook secret. Webhooks live
  in the `notification-webhook` database, which is deliberately *not* polled for changes, so this
  cache is invalidated by `DefaultWebhookStorage` (the only writer of that database) and,
  additionally, expires after `reporting.webhook-subscription-cache.ttl-millis` (default 1000) to
  bound staleness from writes this process cannot see.

  It is intentionally not used by `GET /v1/reporting/webhook` or by `NotificationService`, which
  must always see the current webhook list.
