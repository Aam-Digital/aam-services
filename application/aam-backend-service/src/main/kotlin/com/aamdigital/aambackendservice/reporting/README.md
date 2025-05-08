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
        externalDocChange>"CouchDB doc changed"]
    end
    calculationRequest --> CreateCalculation
    externalDocChange -.-> Q_DocChanges

    Q_DocChanges -.-> ChangeEventConsumer
    ChangeEventConsumer --> CreateCalculation
    CreateCalculation -.-> Q_DocChanges
    Q_DocChanges -.-> Calculation
    Calculation -.-> Q_DocChanges
    
    ChangeEventConsumer --> CalculationChange
    CalculationChange -- if calculation FINISHED_SUCCESS --> WebhookNotification

    Q_DocChanges[[Queue: document.changes.report / report.calculation]]
    ChangeEventConsumer(ReportDocumentChangeEventConsumer)
    CreateCalculation[CreateReportCalculationUseCase]
    Calculation[ReportCalculationUseCase]
    style Calculation fill:#00C853
    CalculationChange[ReportCalculationChangeUseCase]
    WebhookNotification["NotificationService (call Webhooks)"]
```
