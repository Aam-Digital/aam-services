# Notification Module (implementation)

_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/notification.md)_

## Use Case / Flow

1. _Frontend_ (managed by the user through the UI) manages a custom `NotificationConfig:*` document in CouchDB for each user.
2. _Frontend_ makes requests to `NotificationDeviceController` to register individual devices for Push Notifications, which are stored in the `NotificationDeviceRepository`.
    - The frontend also directly registers the device with Firebase Cloud Messaging (FCM)
3. Change detection ([`common/changes`](../common/changes/README.md)) hands every document change in CouchDB to `NotificationDocumentChangeHandler`:
    1. `NotificationConfigCache` keeps notification trigger rules in memory.
       (Cache is loaded from CouchDB on first use and refreshed whenever a `NotificationConfig:*` document changes)
    2. `ApplyNotificationRulesUseCase` checks if a notification rule is triggered.
4. `ApplyNotificationRulesUseCase` runs on consumed `DocumentChangeEvent` and checks if any notification rule is triggered. In that case, it hands a `CreateUserNotificationEvent` per channel to the `UserNotificationPublisher`.
5. `OutboxUserNotificationPublisher` writes the in-app notification immediately (it is a single idempotent CouchDB document, and it is what the user reads) and puts push and email — external calls with second-scale timeouts — into the `notification-outbox` database, a generic [`Outbox`](../common/outbox/README.md).
6. `NotificationOutboxDrainJob` drains the outbox on a short interval; `NotificationOutboxHandler` passes each entry to `CreateNotificationUseCase`, which passes the event on to the applicable `CreateNotificationHandler`.
7. `CreateNotificationHandler` implementations (for push, in-app, email) send the actual notification to the user.

## Email channel

There is no feature flag for email: the channel exists when the configuration allows it.
`@ConditionalOnNotificationEmailConfigured` is the single source of truth, and gates the email handler,
its recipient lookup, the mail sender, the cache and the properties, as well as the `notification.email`
entry of `/actuator/features`. It matches when the notification API is enabled and `spring.mail.host`,
`notification.email.from` and `keycloak.server-url` all have a non-blank value
(`NotificationEmailPrerequisites`). A blank value counts as not set because the deployment template
ships these keys empty, which a plain `@ConditionalOnProperty` would treat as set.

Email is an optional channel, so missing settings never fail startup.
`NotificationEmailStartupDiagnostics` logs which setting is missing (at `ERROR` only when an SMTP host is
set, since then the operator meant to send). `ApplyNotificationRulesUseCase` only emits the email channel
when a registered handler can deliver it, so the rules follow the same condition.

## Rule conditions

A rule's `conditions` are a query in MongoDB syntax, the format the frontend's conditions editor
stores and evaluates them in (e.g. `{"$or": [{"name": {"$not": {"$eq": "Bert"}}}]}`).
They are evaluated with MongoDB's semantics by
[`common/condition/DocumentConditionEngine`](../common/condition/DocumentConditionEngine.kt),
which delegates to the query matcher of [mongo-java-server](https://github.com/bwaldvogel/mongo-java-server)
instead of implementing the operators itself.

- The whole condition tree of a rule is matched at once, so a document matching several `$or`
  branches still triggers one notification per rule.
- A rule with invalid conditions (e.g. an unknown operator) is skipped and logged as a warning,
  without affecting the user's other rules.
- Values are compared strictly by type, as in MongoDB: `{"age": {"$gte": "18"}}` (a string) does
  not match a numeric `age`.
- The legacy array format `{"$elemMatch": "value"}` (or a list of values), which MongoDB rejects,
  is read as `{"$elemMatch": {"$eq": "value"}}` (or `$in`), as the frontend does.

## Delivery guarantees

Notification ids are derived from the document change that caused them
(`documentId`, `rev`, user, rule), so reprocessing a change re-derives the same id: the in-app
document is left alone if it already exists, and re-enqueueing an outbox entry is a no-op. That is
what makes it safe for the change cursor to be replayed.

The generic `Outbox` owns the retry policy; `NotificationOutboxHandler` only decides which
failures are worth retrying. A transient failure (see `TransientNotificationException`) grows
`nextAttemptAt` exponentially; once `attempts` reaches the maximum the entry is *parked* — it stops
being retried but keeps its `lastError` and stays queryable. Parked entries are un-parked once per
process on the first drain, which is what makes "fix the cause and restart" work. Delivered entries
are deleted, so the outbox is empty in steady state.

## Folder Structure

The module follows our standard folder structure, separating controllers, repositories etc.
The actual business logic can be found under **/core**.
