# Aam Digital - Notification API
_for details about the internal implementation [see README in module code folder](../../application/aam-backend-service/src/main/kotlin/com/aamdigital/aambackendservice/notification/README.md)_

## Overview

The Notification Module is watching for events that are configured to trigger (push) notifications to users.
This module watches for database changes and creates notifications through Firebase and also as documents in the database.

### Dependencies

Push Notifications are sent through Firebase Cloud Messaging (FCM).

## API Specification

[notification-api-v1.yaml](../api-specs/notification-api-v1.yaml)

## Setup
The following environment variables are required:
```dotenv
FEATURES_NOTIFICATIONAPI_ENABLED=true
DATABASECHANGEDETECTION_ENABLED=true

# Firebase Configuration: Confidential (!)
NOTIFICATIONFIREBASECONFIGURATION_CREDENTIALFILEBASE64=<base-64-encoded-firebase-credential-file>
```