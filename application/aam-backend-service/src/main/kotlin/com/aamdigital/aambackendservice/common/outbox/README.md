# outbox — Durable Work with Retries

Holds work that must not be lost in a CouchDB database until it has been delivered, and owns the
retry policy, so the module that owes the work only writes its business logic.

## Flow

```text
module code ──enqueue(key, payload)──▶ Outbox  (CouchDB database, one OutboxEntry per key)
                                          │
                                          │  polled by the module's @Scheduled job
                                          ▼
                                    OutboxDrainer ──deliver(payload)──▶ module's OutboxHandler
                                          │
             Delivered: delete entry ◀────┼────▶ RetryLater: back off, then park after maxAttempts
                                          └────▶ Rejected:   park right away
```

- `Outbox<P>` stores `OutboxEntry<P>` documents: the module's payload plus `attempts`,
  `nextAttemptAt` and `lastError`. Re-enqueueing a key that is still waiting is a no-op, so derive
  the key from whatever caused the work and a replay does not produce a duplicate.
  `deliverNowOrEnqueue` tries the handler inline first and only falls back to the outbox on failure.
- `OutboxHandler<P>` is the module's part: deliver one payload and say whether a failure is worth
  retrying (`RetryLater`) or not (`Rejected`).
- `OutboxDrainer<P>` applies the `OutboxRetryPolicy`: exponential backoff, then *parking* the entry
  (kept with its `lastError`, no longer retried). Parked entries are retried once after every
  restart, so the recovery path is always "fix the cause, restart the service".

A successful delivery deletes the entry, so a handler that is not idempotent (sending an email) is
not called twice for it - unless that delete fails, in which case the entry is parked and delivered
again after the next restart.

## Adding an outbox

In the module's `@Configuration`, declare:

1. an `Outbox` bean with its own database name and the payload class, plus a `DatabaseRequest` for
   that database (it is also created on demand);
2. the module's `OutboxHandler`;
3. an `OutboxDrainer` bean combining both with an `OutboxRetryPolicy`.

Then add a `@Scheduled` job that calls `drain()` inside `ScheduledJobBackoff`, and add it to
`SchedulingConfiguration`'s job list and `SCHEDULED_TASKS`. The notification module is the
reference: see `NotificationConfiguration`, `NotificationOutboxHandler` and
`NotificationOutboxDrainJob`.

Keep the database out of `database-change-detection.included-databases` (it is an allowlist, so
this is the default): every drain writes to it.
