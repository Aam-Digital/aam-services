# changes — CouchDB Change Detection & Distribution

Polls the CouchDB `_changes` feed and hands each enriched `DocumentChangeEvent` to every feature module that reacts to data changes.

## Flow

```text
CouchDB _changes feed
        │  (polled every 8 s by CouchDbChangesPollingJob)
        ▼
CouchDbChangesProcessor
   • only polls databases allowlisted in ChangeDetectionProperties (default: "app")
   • fetches current + previous document revision
   • builds DocumentChangeEvent (database, documentId, before/after)
   • calls every DocumentChangeHandler, then advances the sync cursor
        │
        ├──► ReportDocumentChangeHandler        (reporting)
        └──► NotificationDocumentChangeHandler  (notification)
```

## Subscribing to Changes

A feature module receives changes by declaring a `DocumentChangeHandler` bean inside its own
`@Configuration` (see `ReportConfiguration` or `NotificationConfiguration` for examples). Because the
bean only exists when the module's feature flag is on, a disabled module contributes nothing and
needs no extra condition.

Add the module's feature flag to `ChangesConfiguration.AnyChangeConsumerEnabled` as well, so change
detection itself turns on with it.

## What a handler may do

Handlers run **synchronously on the polling thread**, one change at a time, and the sync cursor is
only advanced once every handler has seen the change. A handler must therefore not do slow or
unbounded work inline — no external HTTP, no per-change database scan. Answer from memory (see
`ReportConfigCache`, `WebhookSubscriptionCache`, `NotificationConfigCache`) and hand real work to a
bounded executor or record it durably for a scheduled job to pick up (see `NotificationOutboxDrainer`
and the report calculation executor).

An exception escaping a handler is logged and the remaining handlers still run, so one module cannot
stall change detection for the others. The cursor advances regardless, so a handler is responsible
for its own recovery.

## Key Classes

| Class | Purpose |
| --- | --- |
| `CouchDbChangesPollingJob` | Scheduled trigger (every 8 s), error counting with auto-stop |
| `CouchDbChangesProcessor` | Core logic: poll changes, enrich with doc revisions, call handlers, advance the cursor |
| `ChangeDetectionProperties` | Config: allowlist of databases to poll (`included-databases`) |
| `DocumentChangeEvent` | Event payload: database, documentId, current/previous doc |
| `DocumentChangeHandler` | Interface a feature module implements to react to changes |
| `SyncRepository` / `SyncEntry` | JPA persistence of last processed `update_seq` per database |
| `ChangesConfiguration` | Spring DI wiring for change-detection beans; active whenever a consuming feature module (reporting, notification-api) is enabled |

## Configuration

```yaml
database-change-detection:
  included-databases:
    - app
```

`included-databases` is an allowlist: only databases with an exact name match are polled.
Auxiliary CouchDB databases (e.g. `audit`, `notifications-*`, `notification-outbox`,
`app-attachments`) are intentionally excluded since only the core `app` database is relevant to
current change consumers.
