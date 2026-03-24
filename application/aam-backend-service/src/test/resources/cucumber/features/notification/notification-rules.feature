@Notification
Feature: Notification rule configuration edge cases

    Background:
        Given all default databases are created

    Scenario: Disabled rule does NOT trigger notification when matching document is stored
        Given document NotificationConfig_test-user-disabled is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_1 is stored in database app
        Then the client waits for 5000 milliseconds
        Then user test-user-disabled has 0 notifications in CouchDB

    Scenario: Rule with matching conditions triggers notification
        Given document NotificationConfig_test-user-conditions is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_1 is stored in database app
        Then user test-user-conditions has 1 notification in CouchDB

    Scenario: Rule with non-matching conditions does NOT trigger notification
        Given document NotificationConfig_test-user-conditions is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_2 is stored in database app
        Then the client waits for 5000 milliseconds
        Then user test-user-conditions has 0 notifications in CouchDB
