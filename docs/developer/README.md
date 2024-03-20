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
