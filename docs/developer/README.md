## Getting started

### start local environment

You can start all services needed for the local development with docker-compose:

```shell
docker compose -f docker-compose.yml -p aam-services up -d
```

#### Caddy (reverse-proxy)

Part of the deployed services is a reverse-proxy. If you need to change the behavior, adapt the `./reverse-proxy/Caddyfile`
and restart the reverse-proxy container.

If you need local TLS support, you will need to import the Caddy Root CA.

Install `caddy` on your local machine:

MacOS:
```shell
brew install caddy
```

Trust the Caddy Root CA for local testing:
```shell
# make sure, that the docker container is running
caddy trust
```

### useful tips and tricks

#### Reset http/https redirect cache in chrome

Sometimes, when you're playing around with `http(s)://` redirects in your browser,
Chrome will cache the redirect for some time. When you explicit want to open
the `http://` version of an url, but Chrome will not let you:

- go to `chrome://net-internals/#hsts`
- insert your domain in the `Delete domain security policies` section
- press `delete`

You can open the `http://` version directly again. 


# Project Setup Guide

This guide includes instructions for both a **full local setup with Docker** and a **minimal setup without Docker**.

---

## Full Local Setup (with Docker)

### Step 1: Start Docker Services
1. Run the `docker-compose` file to start all related services:
```shell
   docker-compose up
```
2. If needed, switch the image in `docker-compose.yml` from `aam-sqs-mac` to `aam-sqs-linux` for compatibility.

### Step 2: Configure Keycloak
3. Open the Keycloak Admin UI at [http://localhost:8080](http://localhost:8080) with the credentials defined in the docker-compose file. Defaults are:
```
username - admin
password - docker
```
4. Create a new realm called **dummy-realm** by importing the [realm base configuration file](https://github.com/Aam-Digital/ndb-setup/blob/master/baseConfigs/realm_config.example.json).
5. Under **Keycloak Realm > Clients** ([http://localhost:8080/admin/master/console/#/dummy-realm/clients](http://localhost:8080/admin/master/console/#/dummy-realm/clients)), import the client configuration using the [client config file](https://github.com/Aam-Digital/ndb-setup/blob/master/keycloak/client_config.json).
6. In the new realm, create a user and assign relevant roles. (Usually you will want at least "user_app" and/or "admin_app" role to be able to load the basic app config.)

### Step 3: Set Up CouchDB
7. Access CouchDB at [http://localhost:5984/_utils/#database/app/_all_docs](http://localhost:5984/_utils/#database/app/_all_docs).
8. Create a new database name as `app`.
9. Add a document of type **Config:CONFIG_ENTITY** to the `app` database (e.g., from [dev.aam-digital.net CouchDB instance](https://dev.aam-digital.net/db/couchdb/_utils/#database/app/Config%3ACONFIG_ENTITY)).
**Note: If you get an error while adding a document (eg. document update conflict warning) remove the "_rev": "value".**

### Step 4: Start the Backend
9. Clone the [replication-backend](https://github.com/Aam-Digital/replication-backend) repository (if you do not already have it available locally) and follow the setup instructions of its README to install dependencies.
10. Retrieve the `public_key` for **dummy-realm** from [http://localhost:8080/realms/dummy-realm](http://localhost:8080/realms/dummy-realm) and add it to the `.env` file for the replication backend as `JWT_PUBLIC_KEY`.
10. Start the replication backend:
```shell
    npm run start:dev
```

### Step 5: Start the Frontend
11. Update `environment.ts` or `assets/config.json` with the following settings, in order to run the app in "synced" mode using the backend services:
```
    "session_type": "synced",
    "demo_mode": false
```
12. Start the frontend:
```shell
    npm start
```

### Accessing the Local Environment
- Frontend App: [http://localhost:4200](http://localhost:4200)
- Replication Backend: [http://localhost:3000](http://localhost:3000)
- Keycloak: [http://localhost:8080](http://localhost:8080)
- CouchDB: [http://localhost:5984](http://localhost:5984)

---

## Minimal Setup (Without Docker)

For a basic setup without Docker, follow these steps.

### Step 1: Set Up CouchDB
1. Install CouchDB on Mac:
```shell
   brew install couchdb
```
2. Configure CouchDB:
```shell
   nano /opt/homebrew/etc/local.ini
```
3. Restart CouchDB:
```shell
   brew services restart couchdb
```
4. Access CouchDB at [http://localhost:5984/_utils/#](http://localhost:5984/_utils/#).

### Step 2: Create Databases
1. Create the following databases in CouchDB:
- `app`
- `app-attachments`
- `notification-webhook`
- `Report-calculation`

3. In the `app` database, add these configuration documents:
- **Config:CONFIG_ENTITY**
- **Config:Permissions**

**Note**: Please reach out to the tech team to get the specific details for these configuration documents.

### Step 3: Set Up Replication Backend
- Follow the project setup instructions provided in the [Backend Setup Guideline](https://github.com/Aam-Digital/replication-backend/blob/master/README.md).

Once completed, you’ll have a minimal local environment without Docker.
