# eTutor Task-App: Object-Relational Views

This application provides a REST-interface for following task type: or_view.

Students have to write Oracle Object-Relational View definitions (CREATE OR REPLACE VIEW ... OF ... AS SELECT ...). The student's SQL is compared structurally and content-wise against a reference solution, providing detailed feedback on syntax errors, missing fields, wrong types, incorrect MAKE_REF usage, and more.

## Development

In development environment, the API documentation is available at http://localhost:8081/docs.

In order to run the application in development environment, the `dev` profile must be activated.
This can be done by setting the environment variable `SPRING_PROFILES_ACTIVE` to `dev` or by setting the profile in the IDE run configuration.

### Database Setup

The application requires an Oracle database. Start one locally using Docker Compose:

```bash
docker compose up -d
```

This starts an Oracle Free container on port `1521` with service name `FREEPDB1`.

**Connection details:**
| Property | Value              |
|----------|--------------------|
| Host     | `localhost`        |
| Port     | `1521`             |
| Service  | `FREEPDB1`         |
| User     | `SYSTEM`           |
| Password | `root`             |

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Docker

Start a new instance of the application using Docker:

```bash
docker run -p 8090:8081 \
  -e JDBC_URL="jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1" \
  -e JDBC_ADMIN_USERNAME=SYSTEM \
  -e JDBC_ADMIN_PASSWORD=root \
  -e JDBC_EXECUTOR_USERNAME=SYSTEM \
  -e JDBC_EXECUTOR_PASSWORD=root \
  -e SPRING_DATASOURCE_URL="jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1" \
  -e SPRING_DATASOURCE_USERNAME=SYSTEM \
  -e SPRING_DATASOURCE_PASSWORD=root \
  -e CLIENTS_API_KEYS_0_NAME=task-administration \
  -e CLIENTS_API_KEYS_0_KEY=some-secret-key \
  -e CLIENTS_API_KEYS_0_ROLES_0=CRUD \
  -e CLIENTS_API_KEYS_0_ROLES_1=SUBMIT \
  -e CLIENTS_API_KEYS_1_NAME=moodle \
  -e CLIENTS_API_KEYS_1_KEY=another-secret-key \
  -e CLIENTS_API_KEYS_1_ROLES_0=SUBMIT \
  -e CLIENTS_API_KEYS_2_NAME=plagiarism-checker \
  -e CLIENTS_API_KEYS_2_KEY=key-for-reading-submissions \
  -e CLIENTS_API_KEYS_2_ROLES_0=READ_SUBMISSION \
  etutorplusplus/task-app-object-relational-view
```

or with Docker Compose:

```yaml
services:
  oracle-db:
    image: gvenzl/oracle-free:latest
    environment:
      ORACLE_PASSWORD: root
      APP_USER: SYSTEM
      APP_USER_PASSWORD: root
    ports:
      - '1521:1521'
    volumes:
      - oracle-data:/opt/oracle/oradata
    healthcheck:
      test: ["CMD", "healthcheck.sh"]
      interval: 10s
      timeout: 5s
      retries: 10

  task-app-or-view:
    image: etutorplusplus/task-app-object-relational-view
    restart: unless-stopped
    ports:
      - target: 8081
        published: 8090
    depends_on:
      oracle-db:
        condition: service_healthy
    environment:
      JDBC_URL: jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1
      JDBC_ADMIN_USERNAME: SYSTEM
      JDBC_ADMIN_PASSWORD: root
      JDBC_EXECUTOR_USERNAME: SYSTEM
      JDBC_EXECUTOR_PASSWORD: root
      SPRING_DATASOURCE_URL: jdbc:oracle:thin:@//oracle-db:1521/FREEPDB1
      SPRING_DATASOURCE_USERNAME: SYSTEM
      SPRING_DATASOURCE_PASSWORD: root
      CLIENTS_API_KEYS_0_NAME: task-administration
      CLIENTS_API_KEYS_0_KEY: some-secret-key
      CLIENTS_API_KEYS_0_ROLES_0: CRUD
      CLIENTS_API_KEYS_0_ROLES_1: SUBMIT
      CLIENTS_API_KEYS_1_NAME: moodle
      CLIENTS_API_KEYS_1_KEY: another-secret-key
      CLIENTS_API_KEYS_1_ROLES_0: SUBMIT
      CLIENTS_API_KEYS_2_NAME: plagiarism-checker
      CLIENTS_API_KEYS_2_KEY: key-for-reading-submissions
      CLIENTS_API_KEYS_2_ROLES_0: READ_SUBMISSION

volumes:
  oracle-data:
```

### Environment Variables

The application uses two HikariCP connection pools (admin and executor) for Oracle schema management.

| Variable                      | Description                                                  |
|-------------------------------|--------------------------------------------------------------|
| `SERVER_PORT`                 | The server port (default: 8081).                             |
| `SPRING_DATASOURCE_URL`       | JDBC-URL to the Oracle database.                             |
| `SPRING_DATASOURCE_USERNAME`  | The username for JPA/Hibernate.                              |
| `SPRING_DATASOURCE_PASSWORD`  | The password for JPA/Hibernate.                              |
| `JDBC_URL`                    | JDBC-URL for the evaluation connection pools.                |
| `JDBC_ADMIN_USERNAME`         | The username for the admin connection pool (schema creation). |
| `JDBC_ADMIN_PASSWORD`         | The password for the admin connection pool.                  |
| `JDBC_EXECUTOR_USERNAME`      | The username for the executor connection pool (queries).      |
| `JDBC_EXECUTOR_PASSWORD`      | The password for the executor connection pool.               |
| `JDBC_MAX_POOL_SIZE`          | Max connections per pool (default: 5).                       |
| `JDBC_MAX_LIFETIME`           | Max connection lifetime in ms (default: 1800000).            |
| `JDBC_CONNECTION_TIMEOUT`     | Connection timeout in ms (default: 10000).                   |
| `CLIENTS_API_KEYS_X_NAME`     | The name of the client.                                      |
| `CLIENTS_API_KEYS_X_KEY`      | The API key of the client.                                   |
| `CLIENTS_API_KEYS_X_ROLES_Y`  | The role of the client.                                      |
