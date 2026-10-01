# couchdb — CouchDB Client & Infrastructure

Generic HTTP client and supporting infrastructure for interacting with CouchDB.

## Key Classes

| Class / File | Purpose |
| --- | --- |
| `CouchDbClient` | Interface defining all CouchDB operations (CRUD, `_find`, `_changes`, revisions) |
| `DefaultCouchDbClient` | `RestClient`-based implementation with JSON parsing, error mapping, and ETag concurrency |
| `CouchDbFileStorage` | `FileStorage` implementation using CouchDB document attachments |
| `CouchDbInitializer` | Creates the default databases and those declared as `DatabaseRequest` beans, at startup only |
| `CouchDbHelper` | `documentExists`, `creatingDatabaseIfMissing` and query parameter maps |
| `CouchDbConfiguration` | Spring DI wiring: `RestClient`, `CouchDbClient`, `FileStorage`, initializer beans |
| `CouchDbClientConfiguration` | Externalized connection properties (`basePath`, credentials) |
| `CouchDbDto` | DTOs for CouchDB responses (`DocSuccess`, `FindResponse`, `CouchDbChangesResponse`, etc.) |

## Usage

Inject `CouchDbClient` to interact with CouchDB:

```kotlin
class MyService(private val couchDbClient: CouchDbClient) {
    fun getDoc(id: String): MyEntity =
        couchDbClient.getDatabaseDocument(
            database = "app",
            documentId = id,
            kClass = MyEntity::class
        )
}
```

For file attachments, inject `FileStorage` (backed by `CouchDbFileStorage`).

### Databases created on first write

A database that comes into being with its first document (one per user, like
`notifications_<user>`), or one that may be dropped while the service runs, is not checked for
before every write. Wrap the write in `creatingDatabaseIfMissing` instead:

```kotlin
couchDbClient.creatingDatabaseIfMissing(database) {
    couchDbClient.putDatabaseDocument(database = database, documentId = id, body = document)
}
```

It costs nothing while the database exists. Only when CouchDB answers the write with
`Database does not exist.` (`DATABASE_NOT_FOUND`, as opposed to `NOT_FOUND` for a missing document)
does it create the database and run the write once more. Wrap writes only: a read should treat a
missing database as empty, and a delete must not bring back a database that was dropped.

## Configuration

```yaml
couch-db-client-configuration:
  base-path: http://localhost:5984
  basic-auth-username: admin
  basic-auth-password: password
```
