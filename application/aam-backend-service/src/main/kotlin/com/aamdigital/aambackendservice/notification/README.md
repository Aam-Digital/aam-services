# Notification Module (implementation)

_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/notification.md)_

## Use Case / Flow

1. _Frontend_ (managed by the user through the UI) manages a custom `NotificationConfig:*` document in CouchDB for each user.
2. _Frontend_ makes requests to `NotificationDeviceController` to register individual devices for Push Notifications, which are stored in the `NotificationDeviceRepository`.
    - The frontend also directly registers the device with Firebase Cloud Messaging (FCM)
3. **DOCUMENT_CHANGES_NOTIFICATION_QUEUE** (shared across modules) provides an event whenever documents in the CouchDB change and `NotificationDocumentChangeConsumer` triggers:
    1. `NotificationConfigCache` keeps notification trigger rules in memory.
       (Cache is loaded from CouchDB on startup and refreshed whenever a `NotificationConfig:*` document changes)
    2. `ApplyNotificationRulesUseCase` checks if a notification rule is triggered.
4. `ApplyNotificationRulesUseCase` runs on consumed `DocumentChangeEvent` and checks if any notification rule is triggered. In that case, it hands a `CreateUserNotificationEvent` per channel to the `UserNotificationPublisher`.
5. `OutboxUserNotificationPublisher` writes the in-app notification immediately (it is a single idempotent CouchDB document, and it is what the user reads) and puts push and email — external calls with second-scale timeouts — into the `notification-outbox` database.
6. `NotificationOutboxDrainJob` drains the outbox on a short interval, calling `CreateNotificationUseCase`, which passes the event on to the applicable `CreateNotificationHandler`.
7. `CreateNotificationHandler` implementations (for push, in-app, email) send the actual notification to the user.

## Delivery guarantees

Notification ids are derived from the document change that caused them
(`documentId`, `rev`, user, rule), so reprocessing a change re-derives the same id: the in-app
document is left alone if it already exists, and re-enqueueing an outbox entry is a no-op. That is
what makes it safe for the change cursor to be replayed.

`NotificationOutboxDrainer` owns the retry policy. A transient failure (see
`TransientNotificationException`) grows `nextAttemptAt` exponentially; once `attempts` reaches the
maximum the entry is *parked* — it stops being retried but keeps its `lastError` and stays
queryable. Parked entries are un-parked once per process on the first drain, which is what makes
"fix the cause and restart" work. Delivered entries are deleted, so the outbox is empty in steady
state.

## Folder Structure

The module follows our standard folder structure, separating controllers, repositories etc.
The actual business logic can be found under **/core**.
