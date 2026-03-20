# changes — CouchDB Change Detection & Distribution

Polls the CouchDB `_changes` feed and publishes enriched `DocumentChangeEvent`s to a RabbitMQ fanout exchange so that feature modules can react to data changes.

## Flow

```
CouchDB _changes feed
        │  (polled every 8 s by CouchDbChangesPollingJob)
        ▼
CouchDbChangesProcessor
   • fetches current + previous document revision
   • builds DocumentChangeEvent (database, documentId, before/after)
        │
        ▼
ChangeEventPublisher  ──►  RabbitMQ fanout exchange "document.changes"
                                │
                   ┌────────────┼────────────┐
                   ▼            ▼            ▼
           report queue   notification   (other modules)
                           queue
```

## Subscribing to Changes

Feature modules receive changes by:

1. Declaring a durable queue bound to the `document.changes` fanout exchange (see `ReportQueueConfiguration` or `NotificationQueueConfiguration` for examples).
2. Adding a `@RabbitListener` on the queue that deserializes `DocumentChangeEvent`.

## Key Classes

| Class | Purpose |
|---|---|
| `CouchDbChangesPollingJob` | Scheduled trigger (every 8 s), error counting with auto-stop |
| `CouchDbChangesProcessor` | Core logic: poll changes, enrich with doc revisions, publish |
| `DocumentChangeEvent` | Event payload: database, documentId, current/previous doc |
| `ChangeEventPublisher` | Interface for publishing events to the exchange |
| `DefaultChangeEventPublisher` | RabbitMQ implementation of `ChangeEventPublisher` |
| `SyncRepository` / `SyncEntry` | JPA persistence of last processed `update_seq` per database |
| `ChangesQueueConfiguration` | RabbitMQ exchange and publisher bean definitions |
| `ChangesConfiguration` | Spring DI wiring for change-detection beans |

## Configuration

```yaml
aam-backend:
  changes:
    polling-interval: 8000   # ms between polls
    databases:               # list of CouchDB databases to watch
      - app
```
