# CloudSync - File Storage System

## Overview
CloudSync is a scalable, fault-tolerant file storage system built with Java 17, Spring Boot, Redis, MySQL, and AWS S3.

## Tech Stack
- **Java 17** - LTS version with modern features
- **Spring Boot 3.2.x** - Web framework
- **Spring Data JPA** - Database ORM
- **Spring Security + JWT** - Authentication
- **Spring Data Redis** - Caching layer
- **Resilience4j** - Circuit breaker, retries, rate limiting
- **AWS S3 SDK v2** - File storage
- **MySQL 8.x** - Primary database
- **Lombok** - Boilerplate reduction
- **MapStruct** - DTO mapping

## Architecture
- Multi-tenant: Users belong to organizations
- Consistent hashing for data partitioning (future: multi-node)
- Redis caching for metadata
- S3 for actual file storage with pre-signed URLs
- Resumable uploads via multipart S3

## Key Features
1. User authentication (JWT with refresh tokens)
2. Organization/workspace management
3. File upload/download with resumable multipart support
4. Folder hierarchy management
5. File sharing (internal + external with expiration)
6. Redis caching for metadata
7. Circuit breaker for S3 operations
8. Retry with exponential backoff
9. Consistent hashing for node partitioning

## Project Structure
```
src/main/java/com/cloudsync/
├── CloudSyncApplication.java
├── config/          # Configuration classes
├── controller/       # REST controllers
├── service/          # Business logic
├── repository/       # Data access
├── model/           # JPA entities
├── dto/              # Data transfer objects
├── security/        # JWT, auth filters
├── cache/            # Redis cache logic
├── s3/               # S3 integration
├── partition/        # Consistent hashing
├── resilience/       # Circuit breaker, retries
├── exception/        # Custom exceptions
└── util/             # Utilities
```

## Build & Run
```bash
./mvnw spring-boot:run
./mvnw package -DskipTests
```

## Environment Variables
See application.yml for all configurable properties.
