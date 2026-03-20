@Notification
Feature: Notification rules correctly distinguish created vs updated documents

    Background:
        Given all default databases are created

    Scenario: created rule triggers notification when a new document is stored
        Given document NotificationConfig_test-user is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_1 is stored in database app
        Then user test-user has 1 notification in CouchDB

    Scenario: created rule does NOT trigger notification when an existing document is updated
        Given document NotificationConfig_test-user is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_1 is stored in database app
        Then user test-user has 1 notification in CouchDB
        Given document Child_1 is updated in database app
        Then the client waits for 5000 milliseconds
        Then user test-user has 1 notification in CouchDB

    Scenario: updated rule triggers notification when an existing document is updated
        Given document NotificationConfig_test-user-updated is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_1 is stored in database app
        Then the client waits for 5000 milliseconds
        Then user test-user-updated has 0 notifications in CouchDB
        Given document Child_1 is updated in database app
        Then user test-user-updated has 1 notification in CouchDB
