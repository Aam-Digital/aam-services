## Local setup for SQL query testing

### Requirements

- Docker
- HTTP Client of your choice
- Access to ghcr.io with permissions for Aam Digital private repositories

### Start containers

Take a look into the `docker-compose.yml` and adapt the file if necessary.

If you're on an ARM based system, you need to enable for the SQS container:

- `platform: linux/amd64`

#### Authorization ghcr.io
SQS is not open source and only available to developers of the Aam Digital team through a private docker image.

You will need to do a `docker login` for ghcr.io, to be able to fetch sqs image.

See [GitHub Documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-to-the-container-registry) for details.

### Access CouchDb web interface

You can access the CouchDb webinterface here: http://localhost:5984/_utils/

The default credentials are:

username: `admin`  
password: `docker`

### Using existing Database

If you have a copy from an existing database, stop the couchdb container and just copy all files from the `couchdb/data` directory
into your mapped couchdb volume.
The default mapping is: `~/docker-volumes/aam-digital/db-couch/document-data:/opt/couchdb/data`

Then start the couchdb container again. Nothing more to do.

### Running SQL queries against SQS

To run a Query, you can use the POST endpoint: `http://localhost:4984/app/_design/sqlite:config`

#### Authentication

You need to set a 'basic auth' Authorization-Header with the couchdb credentials.

#### Example

```curl
## Example Request
curl -X "POST" "http://localhost:4984/app/_design/sqlite:config" \
     -H 'Content-Type: application/json; charset=utf-8' \
     -u 'admin:docker' \
     -d $'{
  "query": "SELECT count(*) FROM School",
  "args": []
}'

```
