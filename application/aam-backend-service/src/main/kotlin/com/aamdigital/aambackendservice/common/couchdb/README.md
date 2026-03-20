# couchdb — CouchDB Client & Infrastructure

Generic HTTP client and supporting infrastructure for interacting with CouchDB.

## Key Classes

| Class / File | Purpose |
|---|---|
| `CouchDbClient` | Interface defining all CouchDB operations (CRUD, `_find`, `_changes`, revisions) |
| `DefaultCouchDbClient` | `RestClient`-based implementation with JSON parsing, error mapping, and ETag concurrency |
| `CouchDbFileStorage` | `FileStorage` implementation using CouchDB document attachments |
| `CouchDbInitializer` | Creates required databases on application startup |
| `CouchDbHelper` | Utility functions for building query parameter maps |
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

## Configuration

```yaml
couch-db-client-configuration:
  base-path: http://localhost:5984
  basic-auth-username: admin
  basic-auth-password: password
```
