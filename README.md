# GlanceSync Backend

Backend service for GlanceSync, built with Spark Java 2.9.4.

## Requirements

- Java 17 (JDK)
- Maven 3.6+ (or use the bundled Maven Wrapper `./mvnw`, no local Maven install required)

## Getting Started

Clone the repository and move into the backend directory:

```bash
git clone <repo-url>
cd GlanceSync/backend
```

### Run the application

Using the Maven Wrapper (recommended):

```bash
./mvnw compile exec:java -Dexec.mainClass=com.glancesync.Application
```

Or, if you have Maven installed globally:

```bash
mvn compile exec:java -Dexec.mainClass=com.glancesync.Application
```

The application starts on `http://localhost:8080` by default.

### Run with auto-reload (development)

```bash
./mvnw -Pdev
```

Watches `src/main/java`, `src/main/resources`, `pom.xml` and `.env`. After each saved change it stops
the running app, recompiles, and starts it again. If compilation fails, it waits for the next change.
Dependency changes in `pom.xml` need a full restart of the command. Press Ctrl-C to quit.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` (or the configured server port).

### Build a JAR

```bash
./mvnw package
```

This produces an executable JAR in `target/`. Run it with:

```bash
java -jar target/GlanceSync-0.1.jar
```

### Run tests

```bash
./mvnw test
```

## Configuration

Application configuration lives in `src/main/resources/application.properties`.

## Tech Stack

- Java 17
- Spark Java 2.9.4 (embedded Jetty HTTP server)
- Jackson for JSON serialization
- JDBC with hand-written SQL and HikariCP connection pooling
- Logback for logging
- JUnit 5 for testing

Spark-specific HTTP wiring lives in `com.glancesync.web`; the domain, service, DAO,
request, and response models are independent of the web server.
