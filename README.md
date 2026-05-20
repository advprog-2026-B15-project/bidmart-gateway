# BidMart API Gateway

This is the API Gateway for the BidMart microservices architecture, built using **Spring Cloud Gateway**. It acts as the single entry point for all client requests, providing routing, security, and cross-cutting concerns across the various backend services.

## Core Responsibilities

The API Gateway is responsible for the following core tasks:

1. **Routing**: Dynamically routing incoming HTTP requests to the appropriate downstream microservices (e.g., Catalog Service, Auction Service) based on the URL path.
2. **Authentication & Authorization (JWT Verification)**: 
   - Intercepting incoming requests and extracting the JWT token from the `Authorization: Bearer <token>` header.
   - Validating the JWT signature and expiration.
   - Extracting relevant claims (e.g., User ID, roles) from the validated JWT.
   - Passing user information to downstream services securely via HTTP headers (e.g., `X-User-Id`), preventing the need for individual services to validate tokens.
3. **Load Balancing**: Distributing incoming traffic across multiple instances of backend services to ensure high availability and reliability.
4. **CORS Configuration**: Handling Cross-Origin Resource Sharing (CORS) policies to allow safe requests from frontend applications.

## Prerequisites

- Java 21 (or the version specified in `build.gradle`)
- Gradle
- Running instances of downstream microservices (Catalog, Auction, etc.) for testing routes.

## Getting Started

### 1. Build the Project

To build the project and download all dependencies, run:

```bash
./gradlew build
```

### 2. Run the Gateway Locally

You can start the API Gateway using the standard Spring Boot Gradle plugin:

```bash
./gradlew bootRun
```

By default, the Gateway will start on port `8080` (or as configured in `application.yml`).

## Configuration Overview

Most of the gateway's behavior is configured in `src/main/resources/application.yml` (or `application.properties`).

### Route Configuration Example

Routes are typically defined to forward specific paths to specific services. For example:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: catalog-service
          uri: http://localhost:8081 # Or use lb://catalog-service if using a registry
          predicates:
            - Path=/api/catalog/**
          filters:
            - JwtAuthenticationFilter # Custom filter to verify JWT
            
        - id: auction-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/auctions/**
          filters:
            - JwtAuthenticationFilter
```

### JWT Authentication Filter

The custom filter (often named `JwtAuthenticationFilter` or similar) implements the `GatewayFilter` or `GlobalFilter` interface. Its typical flow is:

1. Check if the route requires authentication (some endpoints like `/api/auth/login` might be public).
2. Extract the `Authorization` header.
3. If missing or invalid, return a `401 Unauthorized`.
4. If valid, parse the JWT, extract the user ID.
5. Mutate the incoming request to add an `X-User-Id` header containing the extracted ID.
6. Forward the mutated request to the downstream service.

## Running Tests

To execute the unit and integration tests:

```bash
./gradlew test
```

## Future Enhancements
- **Rate Limiting**: Implementing Redis-based rate limiting to protect backend services from abuse.
- **Circuit Breaker**: Adding Resilience4j to gracefully handle failures if a downstream service goes offline.
- **Metrics & Tracing**: Integrating Micrometer and Prometheus to monitor gateway traffic and latency.
