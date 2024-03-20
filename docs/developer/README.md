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


---
# initial setup with related services
1. start the docker-compose here
2. if necessary, switch the image in docker-compose.yml from aam-sqs-mac to aam-sqs-linux

Set up Keycloak
3. create dummy-realm in the Keycloak Admin UI (http://localhost:8080), importing the current [ndb-setup realm_config.example.json](https://github.com/Aam-Digital/ndb-setup/blob/master/keycloak/realm_config.example.json)
4. import client in [Keycloak Realm > Clients](http://localhost:8080/admin/master/console/#/dummy-realm/clients), using [ndb-setup client.json](https://github.com/Aam-Digital/ndb-setup/blob/master/keycloak/client_config.json)
5. create a user in the new realm and assign it some relevant roles
6. copy a Config:CONFIG_ENTITY doc into the couchdb: http://localhost:5984/_utils/#database/app/_all_docs (e.g. from https://dev.aam-digital.net/db/couchdb/_utils/#database/app/Config%3ACONFIG_ENTITY)

Start backend:
7. get the public_key for the realm from http://localhost:8080/realms/dummy-realm and add it to the replication-backend .env (JWT_PUBLIC_KEY)
8. start the replication-backend (`npm start:dev`)

Start frontend:
9. switch config (environment.ts or assets/config.json) to `"session_type": "synced", "demo_mode": false`
10. start the frontend (`npm start`)

You now have a fully local environment with all relevant services:
- App (frontend): http://localhost:4200
- Replication backend: http://localhost:3000
- Keycloak: http://localhost:8080
- CouchDB: http://localhost:5984