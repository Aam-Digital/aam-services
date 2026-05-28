@Notification
Feature: Notification email delivery

    Background:
        Given all default databases are created

    Scenario: Email-enabled notification triggers email sending
        Given document NotificationConfig_test-user-email is stored in database app
        Then the client waits for 5000 milliseconds
        Given document Child_1 is stored in database app
        Then user test-user-email has 1 notification in CouchDB
        Then email notification is sent 1 times
