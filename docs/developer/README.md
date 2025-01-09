## Overview

The aam-digital stack contains multiple services from different repositories wich are developed and maintained by aam-digital.  
For some features, we also use third party solutions that are maintained from the respective development team.

### managed by aam-digital (public)

- *ndb-core*: main angular frontend application [GitHub](https://github.com/Aam-Digital/ndb-core)
- *replication-backend*: (optional) layer between frontend and the couchdb for handling document permissions [GitHub](https://github.com/Aam-Digital/replication-backend)
- *aam-backend-services*: main backend spring boot application, modulith architecture [GitHub](https://github.com/Aam-Digital/aam-services/tree/main/application/aam-backend-service)

### managed by aam-digital (private)

Accessible for aam-digital internals and contributors only.

- *aam-external-mock-service*: mock of external systems are subject to a duty of non-disclosure in some cases.

---

### used by aam-digital stack, managed by third party (public)

- *couchdb*: Seamless multi-master syncing database with an intuitive HTTP/JSON API, designed for reliability [GitHub](https://github.com/apache/couchdb)
- *postgresql*: PostgreSQL is an advanced object-relational database management system [GitHub](https://github.com/postgres/postgres)
- *keycloak*: Open Source Identity and Access Management For Modern Applications and Services [GitHub](https://github.com/keycloak/keycloak)
- *rabbitmq-server*: Multi-protocol messaging and streaming broker. [GitHub](https://github.com/rabbitmq/rabbitmq-server)
- *carbone*: Fast, Simple and Powerful report generator in any format [GitHub](https://github.com/carboneio/carbone)

### used by aam-digital stack, managed by third party (private)

Accessible for aam-digital internals and contributors only.

- *structured-query-server (sqs)*: An SQL query engine for CouchDB, letting you use SQL SELECT statements to extract information from a CouchDB database. [Homepage](https://neighbourhood.ie/products-and-services/structured-query-server)

---

### third party tools used for development (public)

- [maildev](https://github.com/maildev/maildev)
- [caddy](https://github.com/caddyserver/caddy)

## Getting started

To make development as simple as possible, we provide all services as docker containers. You can start them with the docker-compose file provided.
All container will communicate directly over the docker network.

---

### reverse-proxy

The stack includes a caddy reverse-proxy that runs on https://aam.localhost/ - SSL is enabled by default. However, this certificate is self-signed
and must be added manually as trustworthy.

You also need to adapt your `/etc/hosts` file and add an entry for `aam.localhost` to `127.0.0.1`:

```bash
sudo nano /etc/hosts
```

Add another line for `aam.localhsot`:

```
127.0.0.1       localhost
127.0.0.1       aam.localhost
```

#### add self-signed certificate

You can add import the auto generated caddy certificate after the aam-stack is started.

##### macos

1. Open Keychain Access (`Cmd` + `Space` and search for it)
2. Switch to System `Keychains` -> `System` -> `Certificates`
   ![Keychain Access](../assets/keychain-access-1.png)
3. Drag and Drop the `./caddy-authorities/root.crt` into Keychain Access
4. Open certificate details by double-click the certificate
5. Trust the certificate for SSL by setting `Trust` -> `Secure Sockets Layer (SSL)` to `Always Trust`  
   ![Keychain Access](../assets/keychain-access-2.png)

## Full local setup with Docker and docker-compose

### Step 1: start the local development stack

You can start all services needed for the local development with docker-compose:

```shell
docker compose -f docker-compose.yml up -d
```

or in the same directory just

```shell
docker compose up -d
```

- If needed, switch the sqs image in `docker-compose.yml` from `aam-sqs-mac` to `aam-sqs-linux` for compatibility.
- Attention: sqs is a private repository for internal use only. If you don't have permissions,
  reach out to us or disable this block in the `docker-compose.yml` file

You can test the running proxy by open [https://aam.localhost/hello](https://aam.localhost/hello) - You should see a welcome message.
When you see a SSL warning, follow the steps in `add self-signed certificate`

### Step 2: Configure Keycloak

- Open the Keycloak Admin UI at [https://aam.localhost/auth](https://aam.localhost/auth) with the credentials defined in the docker-compose file.

  - username: `admin`
  - password: `docker`

- Create a new realm called **dummy-realm** by importing the [realm configuration file](example-data/realm_config.dummy-realm.json).
- Under **Keycloak Realm > Clients** ([https://aam.localhost/auth/admin/master/console/#/dummy-realm/clients](https://aam.localhost/auth/admin/master/console/#/dummy-realm/clients)),
  import the client configuration using [client_app_configuration](example-data/client_app.json).
- In the new realm, create a user and assign relevant roles. (Usually you will want at least "user_app" and/or "admin_app" role to be able to load the basic app config.)

### Step 3: Set Up CouchDB (todo: improve this by automatic script)

- Access CouchDB at [https://aam.localhost/db/couchdb/_utils/#database/app/_all_docs](https://aam.localhost/db/couchdb/_utils/#database/app/_all_docs).
- Create some new databases:
  - `_users`
  - `app`
  - `app-attachmets`
  - `notification-webhook`
  - `report-calculation`
- Add a document of type **Config:CONFIG_ENTITY** to the `app` database (e.g., from [dev.aam-digital.net CouchDB instance](https://dev.aam-digital.net/db/couchdb/_utils/#database/app/Config%3ACONFIG_ENTITY)).
  **Note: If you get an error while adding a document (e.g. document update conflict warning) remove the "_rev": "value".**

- Add a document of type **Config:Permissions** to the `app` database:

```
{
  "_id": "Config:Permissions",
  "data": {
    "public": [
      {
        "subject": [
          "Config",
          "SiteSettings",
          "PublicFormConfig",
          "ConfigurableEnum"
        ],
        "action": "read"
      }
    ],
    "default": [
      {
        "subject": "all",
        "action": "read"
      }
    ],
    "admin_app": [
      {
        "subject": "all",
        "action": "manage"
      }
    ]
  }
}
```

### Step 4: Configure the replication-backend

- If not already done, copy the `.env.example` to `.env`

```shell
# /aam-services/developer
cp .env.example .env
```

- Retrieve the `public_key` for **dummy-realm** from [https://aam.localhost/auth/realms/dummy-realm](https://aam.localhost/auth/realms/dummy-realm) and add it to the `.env` file as `REPLICATION_BACKEND_PUBLIC_KEY`.

```
# from
REPLICATION_BACKEND_PUBLIC_KEY=<the-content-of-"public_key"-from-here-https://aam.localhost/auth/realms/dummy-realm>

# to
REPLICATION_BACKEND_PUBLIC_KEY=MIIBI....
```

- Restart the deployment

```shell
docker compose down && docker compose up -d
```

### Step 5: Start the Frontend

- Update `environment.ts` or `assets/config.json` with the following settings, in order to run the app in "synced" mode using the backend services:

```
session_type: "synced",
demo_mode: false,
account_url: "https://aam.localhost/accounts-backend"
```

- Update `keycloak.json` with the following settings

```
{
  "realm": "dummy-realm",
  "auth-server-url": "https://aam.localhost/auth",
  "ssl-required": "external",
  "resource": "app",
  "public-client": true,
  "confidential-port": 0
}

```

- Start the frontend:

```shell
ng serve --host 0.0.0.0
```

**Attention**

If you use the default `npm start` command, make sure to update the start command in the `package.json` to:

```json
{
  "scripts": {
    "start": "ng serve --host 0.0.0.0"
  }
}
```

### Step 6: (optional)  Configure the notification module of aam-backend-services

1. Download the `firebase-credentials.json` from the firebase interface.
2. Encode it as base64
   run this to print the encoded file to the console:
    ```bash
    base64 -i firebase-credentials.json
    ```
3. Copy the output
4. Create a new file, based on the `secrets.env.example`
    ```bash
    cp secrets.env.example secrets.env
    ```
5. Edit `secrets.env` with an editor of your choice and replace the placeholder with the base-64 output you just copied
    ```
    NOTIFICATIONFIREBASECONFIGURATION_CREDENTIALFILEBASE64=<base-64-encoded-firebase-credential-file>
    ``` 
6. Apply config by restart the containers
    ```bash
    docker compose up -d
    ```

## tips and tricks

### Accessing the Local Environment

- ndb-core (frontend): [https://aam.localhost/](https://aam.localhost/)
- replication-backend: [https://aam.localhost/replication-backend](https://aam.localhost/replication-backend)
  - additional proxy for ndb-core: [https://aam.localhost/db](https://aam.localhost/db)
- aam-backend-service: [https://aam.localhost/api](https://aam.localhost/api)
- maildev (smtp-trap): [https://aam.localhost/maildev/](https://aam.localhost/maildev/)
- Keycloak: [https://aam.localhost/auth](https://aam.localhost/auth)
- CouchDB: [https://aam.localhost/db/couchdb](https://aam.localhost/db/couchdb)
- CouchDB Admin: [https://aam.localhost/db/couchdb/_utils/](https://aam.localhost/db/couchdb/_utils/) (the last "/" is important!)
- RabbitMQ: [https://aam.localhost/rabbitmq/](https://aam.localhost/rabbitmq/) (the last "/" is important!)

### developer credentials

For easy start in local development, we create some default accounts.

Unless otherwise specified, the default credentials are:

- username: `admin`
- password: `docker`

The default credentials for rabbitmq are:

- username: `guest`
- password: `guest`

### Reset http/https redirect cache in chrome

Sometimes, when you're playing around with `http(s)://` redirects in your browser,
Chrome will cache the redirect for some time. When you explicit want to open
the `http://` version of an url, but Chrome will not let you:

- go to `chrome://net-internals/#hsts`
- insert your domain in the `Delete domain security policies` section
- press `delete`

You can open the `http://` version directly again.
