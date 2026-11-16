# Dulo Server

Dulo is a self-hosted app for managing family trees. This repository holds the **server**: a REST
API used by the Dulo web client. Users sign in through an identity provider you already use, such
as Google, Microsoft or a self-hosted one like Pocket ID.

> **Status:** early development. The foundation (build, configuration, database support) and
> sign-in are in place; the initial setup, administration and family tree features are coming
> next.

**Docs:** [rdlf0.github.io/dulo-server](https://rdlf0.github.io/dulo-server/): REST API reference
(Swagger UI), the OpenAPI document for generating clients, the Javadoc, and test and coverage reports.

## Quick start

Dulo is distributed as one Docker image with the server and the web client, built by the web
client project. This repository releases the server JAR that the image runs.

To run the server on its own, download the JAR from the
[latest release](https://github.com/rdlf0/dulo-server/releases/latest) and start it with Java 25.
It stores a SQLite database in `./data`:

```sh
java -jar dulo-server-X.Y.Z.jar
```

Check that it is up:

```sh
curl http://localhost:8080/actuator/health
```

On first start, and until the initial setup is done, the server logs its merged configuration.
Each value shows where it came from (environment variable, config file or default), and secrets
are masked.

Until the initial setup is done, the API answers 503, except for the setup itself. The server logs
a one-time setup token (or uses `dulo.setup.token`). Sign in, then complete the setup with that
token: it creates the tree and the roles, and you become the root user. The
[Authentication](https://rdlf0.github.io/dulo-server/authentication.html) page describes the
steps.

## Configuration

Settings come from three places, in increasing order of precedence:

1. Built-in defaults
2. A YAML file, `/config/dulo.yaml` by default (change the path with `DULO_CONFIG_FILE`)
3. Environment variables

See [`deploy/dulo.example.yaml`](deploy/dulo.example.yaml) for all options. Point your editor at
the [JSON Schema](schema/dulo-config.schema.json) for completion and validation; the example file
shows how.

Environment variable names are the property path in upper case, with dots replaced by `_` and
dashes removed:

| Property                                     | Environment variable                         |
|----------------------------------------------|----------------------------------------------|
| `dulo.database.url`                          | `DULO_DATABASE_URL`                          |
| `dulo.identity.providers.google.client-id`   | `DULO_IDENTITY_PROVIDERS_GOOGLE_CLIENTID`    |
| `dulo.features.map-view`                     | `DULO_FEATURES_MAP_VIEW`                     |

### Database

SQLite in the data directory (`./data` by default, set with `DULO_DATA_DIR`) is the default. For PostgreSQL, MySQL or MariaDB
set the JDBC URL and credentials:

```sh
DULO_DATABASE_URL=jdbc:postgresql://db:5432/dulo
DULO_DATABASE_USERNAME=dulo
DULO_DATABASE_PASSWORD=...
```

The schema is created and upgraded automatically on start.

### Sign-in

Configure at least one identity provider under `dulo.identity.providers` and the web client's
redirect URIs under `dulo.clients.web`. See the
[Authentication](https://rdlf0.github.io/dulo-server/authentication.html) page for how sign-in
works and how to test the API with Postman.

### Features

Features can be forced on or off with `dulo.features.<feature>: true|false`. Forced features are
locked in the administration page; the others are managed by the administrator. Available
features: `people`, `families`, `events`, `places`, `tree-view`, `map-view`, `timeline`,
`audit-log`.

### Logs

Everything goes to standard output: the application log, and one access log line per request
(client address, request, status, size, referrer, user agent and the time taken in
milliseconds). Set `SERVER_TOMCAT_ACCESSLOG_ENABLED=false` to turn the access log off.

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details
(`application/problem+json`), including errors outside the API such as an invalid sign-in request.
A client error's `detail` says what was wrong; for a server error, the log has the details.

## Development

Requirements: JDK 25 and Docker (integration tests use Testcontainers).

```sh
./mvnw verify                                       # format check, unit, architecture and integration tests
./mvnw com.spotify.fmt:fmt-maven-plugin:format      # format the code (Google Java Format, AOSP style)
java -jar dulo-service/target/dulo-server-*.jar     # run locally, data in ./data
```

To run the server with sign-in, use the development configuration
[`deploy/dev/dulo.dev.yaml`](deploy/dev/dulo.dev.yaml) and a mock identity provider. In IntelliJ,
the shared run configuration **Dulo local with mock IdP** (in `.run/`) starts both. Put personal
settings in `deploy/dev/dulo.local.yaml`, which git ignores and which overrides the development
configuration.

The project follows a hexagonal architecture:

| Module                         | Contents                                                                       |
|--------------------------------|--------------------------------------------------------------------------------|
| `dulo-domain`                  | Domain model. Plain Java; its only dependency is JSpecify (null annotations).  |
| `dulo-core`                    | Use cases and ports. Depends only on `dulo-domain`.                            |
| `dulo-adapter-identity`        | OAuth2 authorization server, sign-in through identity providers, token checks. |
| `dulo-adapter-persistence-jpa` | Database adapter: JPA and Liquibase for SQLite, PostgreSQL, MySQL and MariaDB. |
| `dulo-adapter-rest`            | REST API adapter.                                                              |
| `dulo-service`                 | Spring Boot application that wires everything together.                        |

Architecture rules are checked by ArchUnit tests in `dulo-service`.

The OpenAPI document is checked in at [`api/openapi.json`](api/openapi.json), and a running
server also serves it at `/api/v1/openapi.json`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow.

## License

Dulo Server is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE),
with an [additional permission](LICENSE-EXCEPTION) to combine it with Liquibase, whose license
(FSL-1.1-ALv2) is not compatible with the AGPL on its own. If you run a modified version as a
service, the AGPL requires you to offer its source code to your users.
