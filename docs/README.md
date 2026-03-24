# CloudSync - File Storage System

## Overview

CloudSync is an enterprise-grade, scalable file storage system built with Java 17, Spring Boot, Redis, MySQL, and AWS S3. It features fault-tolerant architecture with consistent hashing for data partitioning, Redis caching for optimized metadata retrieval, and resilient patterns including circuit breakers and retries.

## Architecture Highlights

### Core Features

| Feature | Implementation |
|---------|---------------|
| **File Storage** | AWS S3 with pre-signed URLs for secure uploads/downloads |
| **Resumable Uploads** | Multipart S3 uploads with chunk tracking and resumption |
| **Data Partitioning** | Consistent hashing with virtual nodes for even distribution |
| **Caching** | Redis with per-entity TTL policies (up to 24h for file metadata) |
| **Fault Tolerance** | Resilience4j circuit breaker + exponential backoff retries |
| **Authentication** | JWT with access + refresh tokens, 15min/7day expiry |
| **Multi-tenancy** | Organizations with storage quotas and user roles |
| **File Sharing** | Internal/external shares with expiration, passwords, download limits |
| **API Documentation** | OpenAPI 3.0 / Swagger UI at `/swagger-ui.html` |

### System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                  │
│    Web Browser / Mobile App / API Client                               │
│    (Pre-signed URLs for direct S3 upload/download)                    │
└──────────────────────┬─────────────────────────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼─────────────────────────────────────────────────┐
│                       API GATEWAY LAYER                                │
│                  Spring Boot Application                                │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────────┐      │
│  │   Auth API  │  │  File API    │  │   Upload API (Chunks)   │      │
│  │  /auth/*    │  │  /files/*    │  │    /upload/*           │      │
│  └─────────────┘  └──────────────┘  └─────────────────────────┘      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────────┐      │
│  │ Folder API  │  │  Share API   │  │ Organization API       │      │
│  │ /folders/*  │  │  /shares/*  │  │  /organizations/*      │      │
│  └─────────────┘  └──────────────┘  └─────────────────────────┘      │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │              SECURITY LAYER                                    │     │
│  │  JWT Auth Filter → Security Config → Password Encoding        │     │
│  └──────────────────────────────────────────────────────────────┘     │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────────┐     │
│  │              RESILIENCE LAYER                                  │     │
│  │  Circuit Breaker (50% failure rate threshold)                │     │
│  │  Retry (3 attempts, exponential backoff ×2)                   │     │
│  │  Time Limiter (30s timeout per S3 operation)                 │     │
│  └──────────────────────────────────────────────────────────────┘     │
└──────────────────────┬─────────────────────────────────────────────────┘
                       │
       ┌───────────────┼───────────────────────┐
       │               │                       │
┌──────▼──────┐  ┌─────▼───────┐  ┌───────────▼───────────┐
│   MySQL     │  │   Redis     │  │     AWS S3           │
│  (Primary)  │  │  (Cache)    │  │   (File Storage)     │
│             │  │             │  │                      │
│ • Users     │  │ • File      │  │ • File blobs         │
│ • Orgs      │  │   metadata  │  │ • Multipart uploads  │
│ • Files     │  │ • Sessions   │  │ • Pre-signed URLs    │
│ • Folders   │  │ • Dashboard  │  │                      │
│ • Uploads   │  │ • Shares     │  │                      │
│ • Shares    │  │              │  │                      │
│ • Audit     │  │  TTL: 1h-24h │  │                      │
└─────────────┘  └─────────────┘  └──────────────────────┘

### Consistent Hash Ring

```
                    ┌─────────────────────────────────────────────────────┐
                    │              CONSISTENT HASH RING                    │
                    │                                                      │
                    │   Hash(Key) ──────────────────────────────────────── │
                    │       │                                                │
                    │       ▼                                                │
                    │   ┌──────────────────────────────────────────┐         │
                    │   │           Sorted Ring (TreeMap)           │         │
                    │   │                                            │         │
                    │   │  [0]────[VN0]────[VN50]────[VN100]────[MAX] │
                    │   │    │       │        │        │              │         │
                    │   │    ▼       ▼        ▼        ▼              ▼         │
                    │   │  node1   node1    node2    node3           node1     │
                    │   │  (100)   (100)    (100)    (100)           (50)       │
                    │   │                                            │         │
                    │   └────────────────────────────────────────────│         │
                    │                                                    │         │
                    │   For key K, find ceiling(K) = first node →     │         │
                    │   if none, wrap to first entry                  │         │
                    └─────────────────────────────────────────────────────┘
```

### Upload Flow (Resumable Multipart)

```
┌─────────┐    ┌──────────────┐    ┌───────────┐    ┌──────────┐    ┌─────────┐
│  Client │    │  CloudSync    │    │    S3     │    │  Redis   │    │  MySQL  │
└────┬────┘    └───────┬──────┘    └─────┬─────┘    └────┬─────┘    └────┬────┘
     │                  │                  │               │                  │
     │  POST /upload/init                 │               │                  │
     │ ───────────────► │                │               │                  │
     │                  │  InitiateMultipartUpload          │                  │
     │                  │ ─────────────────► │               │                  │
     │                  │ ◄────────────────── │ (uploadId)    │                  │
     │                  │  Create upload_session               │                  │
     │                  │ ─────────────────────────────────────────────────► │
     │                  │  Create upload_parts                 │                  │
     │                  │ ─────────────────────────────────────────────────► │
     │ ◄──────────────── │ {sessionToken, uploadId, chunks}   │                  │
     │ {sessionToken}    │                  │               │                  │
     │                  │                  │               │                  │
     │  POST /upload/chunk (part 1)        │               │                  │
     │  [chunk data]  ─► │                  │               │                  │
     │                  │  UploadPart(part1)               │                  │
     │                  │ ─────────────────► │               │                  │
     │                  │ ◄────────────────── │ (etag)       │                  │
     │                  │  Update part status               │                  │
     │                  │ ─────────────────────────────────────────────────► │
     │ ◄──────────────── │ {progress: 10%}  │               │                  │
     │                  │                  │               │                  │
     │  ... (repeat for each chunk, possibly interrupted) ...               │
     │                  │                  │               │                  │
     │  GET /upload/status/{token} (resumption)         │                  │
     │ ◄────────────────────────────────────────────────────────────────── │
     │  {uploadedChunks: 3, totalChunks: 10}             │                  │
     │                  │                  │               │                  │
     │  POST /upload/chunk (part 4) ───────────────────────────             │
     │                  │                  │               │                  │
     │  ... (resume from chunk 4) ...                    │                  │
     │                  │                  │               │                  │
     │  POST /upload/complete                          │                  │
     │                  │  CompleteMultipartUpload      │                  │
     │                  │ ─────────────────► │               │                  │
     │                  │ ◄────────────────── │             │                  │
     │                  │  Create FileEntity   │               │                  │
     │                  │ ─────────────────────────────────────────────────► │
     │                  │  Update storage quota             │                  │
     │                  │ ─────────────────────────────────────────────────► │
     │ ◄──────────────── │ {fileId, downloadUrl}           │                  │
```

### Circuit Breaker State Machine

```
        ┌──────────────────────────────────────────────────────────────┐
        │                                                              │
        │  ┌─────────┐    failureRate > 50%    ┌──────────────┐       │
        │  │ CLOSED  │ ──────────────────────► │     OPEN      │       │
        │  │ (Normal │ ◄────────────────────── │  (Failing)   │       │
        │  │  Ops)   │    success in 5/10     │              │       │
        │  └─────────┘                        └───────┬──────┘       │
        │       ▲                                   │               │
        │       │                                   │ wait 30s      │
        │       │                                   ▼               │
        │       │                            ┌──────────────┐         │
        │       │ ◄──────────────────────── │  HALF_OPEN   │         │
        │       │   3 calls succeed         │  (Testing)   │         │
        │       │ ─────────────────────────► │              │         │
        │       │   calls fail              └──────────────┘         │
        │       │                                   │                  │
        └───────┼───────────────────────────────────┼──────────────────┘

  CLOSED: Normal operation, all requests pass through
  OPEN:   All requests fail fast (CallNotPermittedException)
  HALF_OPEN: Limited requests allowed to test recovery
```

## Tech Stack

- **Java 17** - Latest LTS with records, sealed classes, pattern matching
- **Spring Boot 3.2.x** - Web, Data JPA, Security, Cache, Actuator
- **Spring Data JPA** - MySQL with HikariCP connection pooling
- **Spring Data Redis** - Lettuce client with connection pooling
- **Spring Security** - JWT authentication with refresh tokens
- **AWS SDK v2** - S3 with TransferManager for multipart
- **Resilience4j** - Circuit breaker, retry, time limiter
- **Redis** - Distributed caching with per-entity TTL
- **MySQL 8.x** - Primary data store
- **OpenAPI 3.0** - Interactive API documentation

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- MySQL 8.x (or use Docker)
- Redis 6+ (or use Docker)
- AWS S3 bucket (or MinIO for local development)

### Local Development

```bash
# 1. Clone and navigate
cd CloudSync

# 2. Set environment variables
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=cloudsync
export DB_USERNAME=root
export DB_PASSWORD=root
export REDIS_HOST=localhost
export REDIS_PORT=6379
export AWS_REGION=us-east-1
export AWS_S3_BUCKET=your-bucket
export AWS_ACCESS_KEY=your-key
export AWS_SECRET_KEY=your-secret
export JWT_SECRET=your-super-secret-key-at-least-256-bits-long

# 3. Build
./mvnw clean package -DskipTests

# 4. Run
./mvnw spring-boot:run
```

### Docker Compose (Full Stack)

```bash
# With MySQL and Redis
docker-compose up -d

# View logs
docker-compose logs -f cloudsync-app

# Stop
docker-compose down
```

### With MinIO (Local S3)

```yaml
# Add to docker-compose.yml
minio:
  image: minio/minio
  ports:
    - "9000:9000"
    - "9001:9001"
  environment:
    - MINIO_ROOT_USER=minioadmin
    - MINIO_ROOT_PASSWORD=minioadmin
  command: server /data --console-address ":9001"
```

## API Reference

### Authentication

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/register` | POST | Register new user |
| `/api/auth/login` | POST | Login and get JWT tokens |
| `/api/auth/refresh` | POST | Refresh access token |
| `/api/auth/logout` | POST | Invalidate session |
| `/api/auth/me` | GET | Get current user |

### Files

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/files` | GET | List files |
| `/api/files/{id}` | GET | Get file details |
| `/api/files/{id}/download` | GET | Stream download |
| `/api/files/{id}/url` | GET | Get pre-signed URL |
| `/api/files/{id}` | PATCH | Rename file |
| `/api/files/{id}` | DELETE | Soft delete (trash) |
| `/api/files/{id}/restore` | POST | Restore from trash |
| `/api/files/{id}/permanent` | DELETE | Permanent delete |
| `/api/files/search` | GET | Search files |
| `/api/files/dashboard` | GET | Get storage dashboard |
| `/api/files/trash` | GET | List trashed files |

### Upload (Resumable)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/upload/init` | POST | Initialize upload session |
| `/api/upload/chunk` | POST | Upload a chunk |
| `/api/upload/chunk/binary` | POST | Upload chunk (binary) |
| `/api/upload/complete` | POST | Complete multipart upload |
| `/api/upload/status/{token}` | GET | Get upload status |
| `/api/upload/pending/{token}` | GET | Get pending chunks |
| `/api/upload/cancel/{token}` | POST | Cancel upload |
| `/api/upload/direct` | POST | Direct upload (small files) |

### Folders

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/folders` | POST | Create folder |
| `/api/folders/{id}` | GET | Get folder contents |
| `/api/folders/root` | GET | List root folders |
| `/api/folders/{id}/children` | GET | List sub-folders |
| `/api/folders/{id}/files` | GET | Files in folder |
| `/api/folders/{id}` | PATCH | Rename folder |
| `/api/folders/{id}` | DELETE | Delete folder |

### Shares

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/shares` | POST | Create share link |
| `/api/shares/file/{id}` | GET | Get shares for file |
| `/api/shares/me` | GET | Get my shares |
| `/api/shares/{id}` | DELETE | Deactivate share |

### Health & Monitoring

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/actuator/health` | GET | Spring health check |
| `/api/health/status` | GET | Detailed system status |
| `/api/health/circuit-breaker` | GET | Circuit breaker metrics |
| `/api/health/partition` | GET | Hash ring info |

## Configuration Reference

All configuration is via `application.yml` with environment variable overrides.

### Database
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

### Redis
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 16
```

### S3
```yaml
aws:
  s3:
    region: ${AWS_REGION}
    bucket: ${AWS_S3_BUCKET}
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}
    presigned-url-expiry: 3600  # 1 hour
    multipart:
      chunk-size: 15MB
      max-concurrency: 10
```

### Resilience
```yaml
resilience4j:
  circuitbreaker:
    instances:
      s3Operations:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      s3Operations:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```

## Deployment

### Docker (Recommended)

```bash
# Production-ready deployment
docker-compose -f docker-compose.yml up -d

# With custom environment
cp .env.example .env
# Edit .env with your credentials
docker-compose up -d
```

### Kubernetes

Helm charts available in `/k8s` directory:

```bash
helm install cloudsync ./k8s/helm/cloudsync \
  --set aws.region=us-east-1 \
  --set aws.bucket=your-bucket \
  --set database.host=your-rds.com \
  --set redis.host=your-redis.com
```

## Monitoring

### Health Endpoints

```bash
# Overall health
curl http://localhost:8080/api/actuator/health

# Circuit breaker status
curl http://localhost:8080/api/health/status

# Detailed metrics
curl http://localhost:8080/api/actuator/metrics
```

### Key Metrics

- **Circuit Breaker**: Failure rate, slow call rate, state
- **Upload Sessions**: Active sessions, completed uploads, failures
- **Storage**: Per-user and per-org usage vs quota
- **Cache Hit Rate**: File metadata cache performance

## Project Structure

```
src/main/java/com/cloudsync/
├── CloudSyncApplication.java
├── config/           # Configuration classes
│   ├── AwsS3Config.java
│   ├── RedisConfig.java
│   ├── SecurityConfig.java
│   ├── OpenApiConfig.java
│   ├── AsyncConfig.java
│   └── WebConfig.java
├── controller/       # REST endpoints
│   ├── AuthController.java
│   ├── FileController.java
│   ├── UploadController.java
│   ├── FolderController.java
│   ├── ShareController.java
│   └── HealthController.java
├── service/         # Business logic
│   ├── AuthService.java
│   ├── FileService.java
│   ├── UploadService.java
│   ├── FolderService.java
│   ├── ShareService.java
│   └── OrganizationService.java
├── repository/      # Data access (JPA)
├── model/entity/    # JPA entities
├── dto/             # Request/Response DTOs
├── security/        # JWT, filters, config
├── cache/           # Redis cache service
├── s3/              # S3 integration
├── partition/       # Consistent hashing
├── resilience/      # Circuit breaker service
├── exception/       # Custom exceptions
└── util/            # Utilities
```

## License

MIT License - See LICENSE file for details.
