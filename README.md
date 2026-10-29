# Natsu Backend

The Spring Boot backend for **Natsu**, a Japanese reading application with Android and iOS clients.

- Android client: [natsudroid](https://github.com/MikoMikocchi/natsudroid)

This repository contains the backend service and a Docker Compose setup for running the application together with its local infrastructure.

## Technology Stack

| Area | Technologies |
| --- | --- |
| Language and framework | Java 26, Spring Boot 4.1.0, Spring MVC |
| Persistence | Spring Data JPA, PostgreSQL |
| Database migrations | Liquibase |
| Security | Spring Security, OAuth2 Authorization Server, OAuth2 Resource Server |
| Caching and rate-limiting integrations | Spring Cache, Redis, Bucket4j |
| Email | Spring Mail, Mailpit for local email testing |
| API documentation | springdoc-openapi / Swagger UI |
| Other libraries | Spring Retry, Jsoup, CommonMark |
| Build | Maven Wrapper |
| Local environment | Docker, Docker Compose |
| Testing support | Spring Boot Test, Testcontainers (PostgreSQL and Redis), GreenMail |

## Prerequisites

To run the full local stack, install:

- [Docker](https://docs.docker.com/get-docker/) with the Docker Compose plugin
- Git

For running the application directly with Maven instead of its container, install a JDK compatible with the project (**Java 26**) and use the included Maven Wrapper.

## Run with Docker Compose

1. Clone the repository and enter the project directory:

   ```bash
   git clone https://github.com/MikoMikocchi/natsu-spring-backend.git
   cd natsu-spring-backend
   ```

2. Build the application image and start the services:

   ```bash
   docker compose up --build -d
   ```

3. Follow the application logs if needed:

   ```bash
   docker compose logs -f app
   ```

The Compose configuration starts the following services:

| Service | Local address / port | Purpose |
| --- | --- | --- |
| Backend | `http://localhost:3000` | Spring Boot application |
| PostgreSQL | `localhost:5432` | Relational database |
| Redis | `localhost:6379` | Cache and Redis-backed integrations |
| Mailpit web UI | `http://localhost:8025` | Inspect emails sent by the application during local development |
| Mailpit SMTP | `localhost:1025` | Local SMTP endpoint used by the application |

Mailpit captures outgoing email locally rather than delivering it to real recipients. Open its web UI at `http://localhost:8025` to inspect messages generated during development.

### Stop the services

Stop the containers while keeping the PostgreSQL volume:

```bash
docker compose down
```

To remove the containers and the persisted development database volume as well:

```bash
docker compose down -v
```

> **Warning:** `docker compose down -v` deletes the local PostgreSQL data volume.

## Run the Application Directly

You can run PostgreSQL, Redis, and Mailpit in Docker while launching Spring Boot from your IDE or terminal:

```bash
docker compose up -d postgres redis mailpit
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` in place of `./mvnw`.

The application's local configuration is intended for dependencies running on `localhost`. Review `src/main/resources/application.yml` before changing the ports or credentials. The environment variables in `docker-compose.yml` override the database, Redis, and mail settings for the containerized application.

## API Documentation

The project includes `springdoc-openapi`. When the documentation endpoints are enabled by the application configuration, Swagger UI is normally available at:

- `http://localhost:3000/swagger-ui/index.html`
- `http://localhost:3000/v3/api-docs`

If these URLs are unavailable, check the Springdoc and Spring MVC settings in `src/main/resources/application.yml`.

## Tests and Code Formatting

Run the test suite:

```bash
./mvnw test
```

Run the full Maven verification lifecycle, including the configured formatting check:

```bash
./mvnw verify
```

To apply the configured Java formatting automatically before verification:

```bash
./mvnw spotless:apply
```

The integration-test dependencies include Testcontainers support for PostgreSQL and Redis. Tests that start containers require Docker to be available.

## Configuration and Security Notes

- The PostgreSQL username, password, and database in `docker-compose.yml` are development defaults only. Do not reuse them in a shared or production environment.
- Do not commit real credentials, OAuth2 keys/secrets, SMTP credentials, or other sensitive configuration to the repository.
- Before deploying outside a local development environment, review authentication and authorization settings, CORS/CSRF behavior where applicable, exposed ports, database credentials, email configuration, and TLS termination.

## License

MIT
