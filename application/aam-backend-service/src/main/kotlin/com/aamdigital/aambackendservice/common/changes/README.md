# changes — CouchDB Change Detection & Distribution

Polls the CouchDB `_changes` feed and hands each enriched `DocumentChangeEvent` to every feature module that reacts to data changes — separately for each module, with its own cursor.

## Flow

```text
CouchDB _changes feed
        │  (polled every 8 s by CouchDbChangesPollingJob — one task per handler,
        │   each on its own scheduler thread and reading from its own cursor)
        ├──────────────────────────────┐
        ▼                              ▼
CouchDbChangesProcessor         CouchDbChangesProcessor
 cursor SyncEntry:app:reporting  cursor SyncEntry:app:notification
        │                              │
        ▼                              ▼
ReportDocumentChangeHandler     NotificationDocumentChangeHandler
```

For each handler, `CouchDbChangesProcessor`:

- only polls databases allowlisted in `ChangeDetectionProperties` (default: `app`)
- fetches the current and previous document revision
- builds a `DocumentChangeEvent` (database, documentId, before/after)
- calls the handler, then advances that handler's cursor

The feed is the durable event log: each module keeps its own position in it, the way each queue
bound to a fanout exchange used to. A module blocked on a slow dependency therefore holds back only
itself.

## Subscribing to Changes

A feature module receives changes by declaring a `DocumentChangeHandler` bean inside its own
`@Configuration` (see `ReportConfiguration` or `NotificationConfiguration` for examples). Because the
bean only exists when the module's feature flag is on, a disabled module contributes nothing and
needs no extra condition.

Give the handler a `consumerName` that is unique among handlers. It names the handler's cursor
document, `SyncEntry:<database>:<consumerName>`, so it must never be renamed: a renamed handler
finds no cursor and starts again from "now", skipping whatever changed in between. That is also
what a newly enabled module does on its first poll.

Add the module's feature flag to `ChangesConfiguration.AnyChangeConsumerEnabled` as well, so change
detection itself turns on with it, and raise `SchedulingConfiguration`'s `poolSize` by one for the
new polling task.

## What a handler may do

Handlers run **synchronously on their own polling thread**, one change at a time, and the
handler's cursor is advanced once it has handled the change. A slow handler delays only its own
module — but it does delay it, so a handler must not do slow or unbounded work inline: no per-change
database scan, and any external call bounded by a timeout. Answer from memory (see
`ReportConfigCache`, `WebhookSubscriptionCache`, `NotificationConfigCache`) and hand real work to a
bounded executor or record it durably for a scheduled job to pick up (see `NotificationOutboxDrainer`
and the report calculation executor).

An exception escaping a handler is logged at ERROR and the handler's cursor advances past the
change, so one document a module cannot handle does not stall that module's feed. The change is
therefore not redelivered, and a handler is responsible for its own recovery.

## Upgrading from the shared cursor

Before every handler had its own cursor, all of them shared `SyncEntry:<database>`.
`SharedSyncEntryMigration` runs before the first poll: it copies that position to every registered
handler that has no cursor yet and then deletes it, so a module enabled later starts from "now"
instead of inheriting an old position. It is transitional and goes together with the PostgreSQL
migration.

## Key Classes

| Class | Purpose |
| --- | --- |
| `CouchDbChangesPollingJob` | One scheduled task per handler (every 8 s), each with its own backoff (`ChangeConsumerPoller`) |
| `CouchDbChangesProcessor` | Core logic, per handler: poll changes, enrich with doc revisions, call the handler, advance its cursor |
| `ChangeDetectionProperties` | Config: allowlist of databases to poll (`included-databases`) |
| `DocumentChangeEvent` | Event payload: database, documentId, current/previous doc |
| `DocumentChangeHandler` | Interface a feature module implements to react to changes; `consumerName` names its cursor |
| `SyncRepository` / `SyncEntry` | last processed `update_seq` per database and handler, stored in `aam-backend-state` |
| `SharedSyncEntryMigration` | Transitional: splits the cursor all handlers used to share into one per handler |
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
