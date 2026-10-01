# IntelliJ run and test runbook

## Import

1. Open the directory containing `pom.xml`.
2. Select JDK 21 for Project SDK and Maven importer JDK.
3. Reload the Maven project.
4. Wait for dependency indexing to finish.

## Local database

```bat
docker compose up -d
docker compose ps
```

Reset all local data only when intentionally starting over:

```bat
docker compose down -v
docker compose up -d
```

## Run application

Run `ConsentCoreApplication.main()` or:

```bat
mvnw.cmd spring-boot:run
```

A successful startup includes Flyway migration output and:

```text
Started ConsentCoreApplication
```

## Verify

```text
http://localhost:8081/actuator/health
http://localhost:8081/swagger-ui.html
```

## Run tests

Full verification:

```bat
mvnw.cmd clean verify
```

Selected tests:

```bat
mvnw.cmd -Dtest=ConsentInvariantTest test
mvnw.cmd -Dtest=ConsentApplicationServiceTest test
mvnw.cmd -Dtest=ArchitectureTest test
mvnw.cmd -Dtest=TrustedRegistrationIntegrationTest test
mvnw.cmd -Dtest=EvidenceAuthorizationIntegrationTest test
```

Testcontainers tests require Docker Desktop.

## API test

Open `http/consent-core.http`, select environment `local`, and run the numbered requests in order.
