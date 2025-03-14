# Notification Module (implementation)
_for details about setup & usage of this module [see README in docs folder](../../../../../../../../../docs/modules/notification.md)_

## Use Case / Flow
1. _Frontend_ (managed by the user through the UI) manages a custom `NotificationConfigEntity` document in the CouchDB for each user.
2. _Frontend_ makes requests to `NotificationDeviceController` to register individual devices for Push Notifications, which are stored in the `NotificationDeviceRepository`.
   - The frontend also directly registers the device with Firebase Cloud Messaging (FCM)
3. **DOCUMENT_CHANGES_NOTIFICATION_QUEUE** (shared across modules) provides an event whenever documents in the CouchDB change and `NotificationDocumentChangeConsumer` triggers:
   1. `SyncNotificationConfigUseCase` keeps all notification trigger rules in the `NotificationConfigRepository` up-to-date.
   2. `ApplyNotificationRulesUseCase` checks if a notification rule is triggered.
4. `ApplyNotificationRulesUseCase` runs on consumed `DocumentChangeEvent` and checks if any notification rule is triggered. In that case, it publishes a `CreateUserNotificationEvent` in the queue.
5. `CreateNotificationUseCase` runs on consumed `CreateUserNotificationEvent` and passes the event on to all applicable handlers for different notification channels.
6. `CreateNotificationHandler` implementations (for push, in-app) send the actual notification to the user.

## Folder Structure
The module follows our standard folder structure, separating controllers, repositories etc.
The actual business logic can be found under **/core**.
