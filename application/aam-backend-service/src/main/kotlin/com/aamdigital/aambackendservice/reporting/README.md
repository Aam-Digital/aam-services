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
