# GlanceSync Backend

Backend service for GlanceSync, built with [Micronaut](https://micronaut.io) 4.10.17.

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
./mvnw mn:run
```

Or, if you have Maven installed globally:

```bash
mvn mn:run
```

The application starts on `http://localhost:8080` by default.

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
- Micronaut 4.10.17 (Netty HTTP server)
- Micronaut Serde (Jackson) for JSON serialization
- Logback for logging
- JUnit 5 for testing

## Documentation

- [Micronaut User Guide](https://docs.micronaut.io/4.10.17/guide/index.html)
- [Micronaut API Reference](https://docs.micronaut.io/4.10.17/api/index.html)
- [Micronaut Configuration Reference](https://docs.micronaut.io/4.10.17/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)
- [Micronaut Maven Plugin](https://micronaut-projects.github.io/micronaut-maven-plugin/latest/)
