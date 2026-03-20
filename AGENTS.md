# AI Agent Instructions

You are an expert Kotlin and Spring Boot developer working on Aam Digital's backend services. Follow these project-specific guidelines and best practices.

## Project Context

Aam Digital is a comprehensive case management software for social organizations, designed to improve effectiveness and transparency in work with beneficiaries.
It is designed offline-first, as a progressive-web-app (PWA) using CouchDB for data storage and synchronization.
Some advanced features require backend services, which are provided by this repository.

This repository provides the backend API as a modularized Spring Boot application for [Aam Digital's case management platform](https://github.com/Aam-Digital/ndb-core).

### Related Repositories

- **[ndb-core](https://github.com/Aam-Digital/ndb-core)** — Angular frontend application
- **[replication-backend](https://github.com/Aam-Digital/replication-backend)** — CouchDB replication and permission proxy
- **[ndb-setup](https://github.com/Aam-Digital/ndb-setup)** — Docker-based deployment and infrastructure setup

### Architecture & Tech Stack

- **Language**: Kotlin (target JVM 21)
- **Framework**: Spring Boot with Spring Security, Spring Data JPA
- **Build Tool**: Gradle with Kotlin DSL
- **Database**: CouchDB with SQL query capabilities (SQS), PostgreSQL via JPA
- **Message Queue**: RabbitMQ (AMQP)
- **Testing**: JUnit 5 with Mockito and AssertJ, Cucumber for BDD
- **Code Quality**: Detekt for static analysis, JaCoCo for coverage
- **Architecture**: Clean Architecture with Domain-Driven Design principles
- **Observability**: Micrometer, SLF4J, Spring Actuator, OpenTelemetry

### Key Features

- Template-based file export (carbone.io)
- SQL reporting and analytics
- Push notification system with custom rules
- SkillLab external system integration
- Third-party SSO authentication
- Feature-flag-based module enablement

---

## Project & File Structure

```
aam-services/
├── application/
│   ├── aam-backend-service/          # Main Spring Boot application
│   │   ├── build.gradle.kts
│   │   ├── detekt-config.yml
│   │   ├── Dockerfile
│   │   └── src/
│   │       ├── main/kotlin/com/aamdigital/aambackendservice/
│   │       │   ├── common/           # Shared infrastructure and domain services
│   │       │   ├── export/           # Template-based file export module
│   │       │   ├── notification/     # Push notification system
│   │       │   ├── reporting/        # SQL reporting and analytics
│   │       │   ├── skill/            # SkillLab integration
│   │       │   └── thirdpartyauthentication/  # SSO integration
│   │       ├── main/resources/
│   │       │   └── application.yaml  # Spring Boot configuration
│   │       └── test/kotlin/          # Unit and integration tests
│   └── keycloak-third-party-authentication/  # Keycloak SPI module
├── docs/
│   ├── api-specs/                    # OpenAPI specifications
│   ├── developer/                    # Local development setup (docker-compose, etc.)
│   └── modules/                      # Admin/deployment documentation per module
└── templates/                        # Template files for export module
```

---

## Architecture Patterns

### Module Structure

The application follows a modular monolith ("modulith") pattern with feature-based packages:

```
com.aamdigital.aambackendservice/
├── common/           # Shared infrastructure and domain services
├── export/           # Template-based file export module
├── notification/     # Push notification system
├── reporting/        # SQL reporting and analytics
├── skill/            # SkillLab integration
└── thirdpartyauthentication/  # SSO integration
```

Each module is self-contained with its own controllers, services, and domain logic.
A module can use common services but should not depend on other modules.
Modules are located under `com.aamdigital.aambackendservice.<module>`.

### Package Organization

```
module/
├── controller/       # REST endpoints
├── di/               # Configuration of dependency injection
├── storage/          # Repositories and data access
├── queue/            # Message queue wiring with listeners and publishers
├── usecase/          # Domain logic and use cases
└── README.md         # Module-specific developer documentation
```

### Domain Architecture

Follow Clean Architecture principles with clear separation of concerns:

1. **Controllers**: Handle HTTP requests, validation, and response formatting
2. **Core/Domain**: Business logic implemented as Use Cases
3. **Infrastructure**: External service integrations, repositories, and adapters

### Use Case Pattern

All business logic should be implemented using the Use Case pattern:

```kotlin
// Request/Response Data Classes
data class CreateExampleRequest(
    val field: String,
) : UseCaseRequest

data class CreateExampleData(
    val result: DomainReference,
) : UseCaseData

// Error Codes
enum class CreateExampleError : AamErrorCode {
    VALIDATION_FAILED,
    EXTERNAL_SERVICE_ERROR
}

// Abstract Use Case
abstract class CreateExampleUseCase :
    DomainUseCase<CreateExampleRequest, CreateExampleData>()
```

The `DomainUseCase` base class handles error wrapping:

- Override `apply(request)` to implement business logic
- Return `UseCaseOutcome.Success(data)` or `UseCaseOutcome.Failure(errorCode, errorMessage)`
- Uncaught exceptions are automatically wrapped as `Failure`

---

## Coding Standards

### Code Style (Detekt Configuration)

- **Complexity**: Methods should not exceed 60 lines, classes should not exceed 600 lines
- **Cyclomatic Complexity**: Maximum 14 per method
- **Parameter Limits**: 5 for functions, 6 for constructors
- **Nesting Depth**: Maximum 4 levels
- **Conditional Complexity**: Maximum 3 conditions per expression

### Naming Conventions

- Use descriptive, domain-specific names
- Controllers end with `Controller`
- Use Cases end with `UseCase`
- Error enums end with `Error`
- Data classes use clear, intention-revealing names

### Refactoring & Legacy Code

- Some existing code may not follow current conventions. For existing code, analyse the status and refactor only after confirmation.
- Always separate refactoring changes into their own commits and PRs — do not mix refactoring with feature work.

---

## Testing Guidelines

### Test Structure

- Use JUnit 5 with `@ExtendWith(MockitoExtension::class)`
- Follow Given-When-Then pattern in test methods
- Use AssertJ for assertions: `Assertions.assertThat()`
- Mock external dependencies with Mockito

### Test Naming

```kotlin
@Test
fun `should return success when valid request is provided`() {
    // Given
    val request = CreateExampleRequest("valid-data")

    // When
    val result = useCase.execute(request)

    // Then
    assertThat(result).isInstanceOf(Success::class.java)
}
```

### Test Coverage

- Unit tests for all use cases and business logic
- Integration tests for controllers and external service interactions
- Mock external dependencies (databases, message queues, HTTP clients)
- Cucumber BDD tests for end-to-end scenarios

---

## Security Guidelines

### Authentication & Authorization

- Use Spring Security with OAuth2 Resource Server
- Implement method-level security with `@EnableMethodSecurity`
- Validate all inputs using Spring Validation annotations
- Handle authentication errors gracefully

### Error Handling

Use domain-specific error codes and the `UseCaseOutcome` sealed interface:

```kotlin
// Domain-specific error codes
enum class ValidationError : AamErrorCode {
    INVALID_INPUT,
    MISSING_REQUIRED_FIELD
}

// UseCaseOutcome pattern
sealed interface UseCaseOutcome<D : UseCaseData> {
    data class Success<D : UseCaseData>(val data: D) : UseCaseOutcome<D>
    data class Failure<D : UseCaseData>(
        val errorCode: AamErrorCode,
        val errorMessage: String,
        val cause: Throwable? = null
    ) : UseCaseOutcome<D>
}
```

---

## API Design

### REST Controllers

```kotlin
@RestController
@RequestMapping("/api/v1/module")
@ConditionalOnProperty(name = ["features.module.enabled"], havingValue = "true")
class ExampleController(
    private val useCase: ExampleUseCase
) {
    @PostMapping
    fun createExample(
        @Valid @RequestBody request: CreateExampleRequestDto
    ): ResponseEntity<*> {
        return when (val result = useCase.execute(request.toDomain())) {
            is Success -> ResponseEntity.ok(result.data.toDto())
            is Failure -> ResponseEntity.badRequest().body(
                HttpErrorDto(result.errorCode, result.errorMessage)
            )
        }
    }
}
```

### Response Handling

- Use `ResponseEntity` for HTTP responses
- Implement proper HTTP status codes
- Return structured error responses with `HttpErrorDto`
- Support streaming responses for large data with `StreamingResponseBody`

---

## Configuration & Feature Flags

### Module Enablement

All modules should be conditionally enabled via feature flags:

```kotlin
@ConditionalOnProperty(name = ["features.module.enabled"], havingValue = "true")
```

The application must not initialize any code or require any configuration parameters
for modules that are disabled.

Each feature module should implement a `FeatureRegistrar` to publish its feature status.

### Configuration Properties

Use `@ConfigurationProperties` for module-specific configuration:

```kotlin
@ConfigurationProperties(prefix = "aam.module")
data class ModuleConfiguration(
    val enabled: Boolean = false,
    val apiUrl: String,
    val timeout: Duration = Duration.ofSeconds(30)
)
```

---

## Message Queue Integration

### RabbitMQ Patterns

- Use `@RabbitListener` for consuming messages
- Implement dead letter queues for error handling
- Use appropriate exchange types (direct, topic, fanout)
- Handle message acknowledgments properly

```kotlin
@RabbitListener(queues = ["queue.name"])
fun handleMessage(
    @Payload message: MessageDto,
    @Header headers: Map<String, Any>
) {
    // Process message
}
```

---

## Database Integration

### CouchDB Integration

- Use the common CouchDB client for database operations
- Implement document change listeners for reactive processing
- Handle document versioning and conflicts appropriately
- Use SQS for complex SQL queries

### JPA Integration

- Use Spring Data JPA for relational data (PostgreSQL)
- Implement proper transaction boundaries
- Use `@Transactional` appropriately
- Follow repository pattern for data access

---

## Documentation Requirements

### Code Documentation

- Document public APIs with clear JavaDoc/KDoc
- Include module-specific README.md files
- Document configuration properties and their purpose
- Provide usage examples for complex features

### Admin / Usage Documentation

- Document configuration and deployment steps in a module README in `docs/modules/`
- This README is for administrators to understand module purpose, configuration and deployment
- Developer documentation about the implementation should be separate under the module's code folder

### API Documentation

- Generate OpenAPI specifications for REST endpoints
- Include example requests and responses
- Document error codes and their meanings
- Keep API specifications in `docs/api-specs/`

---

## Performance & Monitoring

### Observability

- Include Micrometer for metrics collection
- Use structured logging with SLF4J
- Implement health checks with Spring Actuator
- Add distributed tracing with OpenTelemetry

### Best Practices

- Use connection pooling for database connections
- Implement proper caching strategies
- Handle large data sets with streaming or pagination
- Monitor memory usage and garbage collection

---

## Deployment & Environment

### Docker Support

- Include Dockerfile for containerization
- Use multi-stage builds for smaller images
- Configure proper health checks
- Support environment-specific configuration

### Configuration Management

- Use Spring Profiles for environment-specific settings
- Externalize configuration via environment variables
- Provide sensible defaults for development
- Document required environment variables

---

## Common Pitfalls to Avoid

1. **Blocking Operations**: Don't perform blocking I/O in reactive contexts
2. **Memory Leaks**: Properly close resources and streams
3. **Security**: Never log sensitive information (passwords, tokens)
4. **Testing**: Don't test Spring framework behavior, focus on business logic
5. **Error Handling**: Don't swallow exceptions without proper logging
6. **Performance**: Avoid N+1 queries and unnecessary database calls

---

## Common Commands

- `./gradlew build` — Full build (compile, test, checks)
- `./gradlew test` — Run all tests
- `./gradlew jacocoTestReport` — Run tests with coverage report
- `./gradlew detekt` — Run static analysis

All Gradle commands should be run from `application/aam-backend-service/`.

For local development setup (databases, queues, Keycloak), see `docs/developer/README.md` and the docker-compose files there.

### Running a Single Unit Test Class

```bash
./gradlew test --tests "com.aamdigital.aambackendservice.export.usecase.DefaultCreateTemplateUseCaseTest"
```

### Running Specific Cucumber (E2E) Tests

Use environment variables to filter Cucumber scenarios. Always combine with `--tests "*CucumberTestRunner"` to skip unit tests.

```bash
# By tag — add e.g. @Focus to the scenario in the .feature file, then filter
CUCUMBER_FILTER_TAGS="@Focus" ./gradlew test --tests "*CucumberTestRunner"

# By existing tag (e.g. @Notification)
CUCUMBER_FILTER_TAGS="@Notification" ./gradlew test --tests "*CucumberTestRunner"

# By scenario name (regex match)
CUCUMBER_FILTER_NAME="client makes call to start a report calculation" ./gradlew test --tests "*CucumberTestRunner"

# By feature file path (optionally with :line to target a single scenario)
CUCUMBER_FEATURES="src/test/resources/cucumber/features/notification/notification-change-type.feature:8" ./gradlew test --tests "*CucumberTestRunner"
```

Note: Cucumber E2E tests use Testcontainers, so Docker must be running. Container startup adds ~30-60s overhead regardless of how many scenarios run.

### Working with Test Results

Gradle generates structured test reports that persist after each run. To avoid re-running tests for further analysis, read the existing report files:

- **JUnit XML reports**: `build/test-results/test/` — machine-readable per-class results
- **JaCoCo coverage**: `build/reports/jacoco/test/jacocoTestReport.xml` (XML) and `.csv` (CSV)

When running tests, always pipe console output to a file for later reference:

```bash
./gradlew test 2>&1 | tee build/test-output.log
```

## Examples and Templates

When generating code, refer to existing patterns in the codebase:

- Controllers: `TemplateExportController.kt`
- Use Cases: `CreateTemplateUseCase.kt`
- Tests: `BasicDomainUseCaseTest.kt`
- Configuration: `application.yaml`
