# Copilot Instructions for Aam Services Backend/API

This document provides context and guidelines for AI code generation in the Aam Digital backend (aam-services)
repository.

## Project Overview

Aam Digital is a comprehensive case management software for social organizations, designed to improve effectiveness and
transparency in work with beneficiaries.
It is designed offline-first, as a progressive-web-app (PWA) using CouchDB for data storage and synchronization.
Some advanced features require backend services, which are provided by this repository.

### Key Technologies

A modularized Spring Boot application providing API modules for Aam Digital's case management platform.
The system uses a microservices architecture with asynchronous processing via RabbitMQ and CouchDB
for data persistence.

- **Language**: Kotlin (target JVM 21)
- **Framework**: Spring Boot 3.3.4 with Spring Security, Spring Data JPA
- **Build Tool**: Gradle with Kotlin DSL
- **Database**: CouchDB with SQL query capabilities (SQS)
- **Message Queue**: RabbitMQ (AMQP)
- **Testing**: JUnit 5 with Mockito and AssertJ
- **Code Quality**: Detekt for static analysis
- **Architecture**: Clean Architecture with Domain-Driven Design principles

## Architecture Patterns

### Module Structure

The application follows a modular monolith ("modulith") pattern with feature-based packages:

```
com.aamdigital.aambackendservice/
├── common/           # Shared infrastructure and domain services
├── export/           # Template-based file export module
├── notification/     # Push notification system
├── reporting/        # SQL reporting and analytics
├── ...               # Additional feature modules
```

Each module is self-contained with its own controllers, services, and domain logic.
A module can use common services but should not depend on other modules.
Modules are located under `com.aamdigital.aambackendservice.<module>`.

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

## Security Guidelines

### Authentication & Authorization

- Use Spring Security with OAuth2 Resource Server
- Implement method-level security with `@EnableMethodSecurity`
- Validate all inputs using Spring Validation annotations
- Handle authentication errors gracefully

### Error Handling

```kotlin
// Use domain-specific error codes
enum class ValidationError : AamErrorCode {
    INVALID_INPUT,
    MISSING_REQUIRED_FIELD
}

// Return structured error responses
sealed interface UseCaseOutcome<out T> {
    data class Success<T>(val data: T) : UseCaseOutcome<T>
    data class Failure<T>(val error: AamErrorCode, val message: String) : UseCaseOutcome<T>
}
```

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
                HttpErrorDto(result.error, result.message)
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

## Database Integration

### CouchDB Integration

- Use the common CouchDB client for database operations
- Implement document change listeners for reactive processing
- Handle document versioning and conflicts appropriately
- Use SQS for complex SQL queries

### JPA Integration

- Use Spring Data JPA for relational data
- Implement proper transaction boundaries
- Use `@Transactional` appropriately
- Follow repository pattern for data access

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

#### API Documentation

- Generate OpenAPI specifications for REST endpoints
- Include example requests and responses
- Document error codes and their meanings
- Keep API specifications in `/docs/api-specs/`

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

## Common Pitfalls to Avoid

1. **Blocking Operations**: Don't perform blocking I/O in reactive contexts
2. **Memory Leaks**: Properly close resources and streams
3. **Security**: Never log sensitive information (passwords, tokens)
4. **Testing**: Don't test Spring framework behavior, focus on business logic
5. **Error Handling**: Don't swallow exceptions without proper logging
6. **Performance**: Avoid N+1 queries and unnecessary database calls

## Examples and Templates

When generating code, refer to existing patterns in the codebase:

- Controllers: `TemplateExportController.kt`
- Use Cases: `CreateTemplateUseCase.kt`
- Tests: `BasicDomainUseCaseTest.kt`
- Configuration: Application configuration in `application.yml`

Follow these guidelines to maintain consistency, quality, and maintainability across the Aam Services codebase.
