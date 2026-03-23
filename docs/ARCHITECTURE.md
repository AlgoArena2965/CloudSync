# CloudSync Architecture Documentation

## 1. System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│   Web Browser (Direct S3 Upload)  │  Mobile App  │  API Client (curl/http) │
└─────────────────────────────────────┴──────────────┴────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           LOAD BALANCER                                    │
│                    (nginx / AWS ALB / CloudFlare)                            │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────────────────┐
│                        SPRING BOOT APPLICATION                               │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                       API LAYER                                         │ │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐│ │
│  │  │  Auth   │  │  Files  │  │ Upload  │  │ Folders  │  │ Shares  ││ │
│  │  │Controller│  │Controller│  │Controller│  │Controller│  │Controller││ │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘│ │
│  └───────┼──────────────┼──────────────┼──────────────┼──────────────┼────┘ │
│  ┌───────▼──────────────▼──────────────▼──────────────▼──────────────▼────┐ │
│  │                        SERVICE LAYER                                     │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │ │
│  │  │AuthService│ │FileService│ │UploadSvc │ │FolderSvc │ │ShareSvc  │  │ │
│  │  └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────┬─────┘  │ │
│  └────────┼─────────────┼─────────────┼─────────────┼─────────────┼────────┘ │
│  ┌────────▼─────────────▼─────────────▼─────────────▼─────────────▼────────┐ │
│  │                       RESILIENCE LAYER                                    │ │
│  │   Circuit Breaker (50% failure → OPEN)  │  Retry (3x, exp backoff)      │ │
│  │   Time Limiter (30s timeout per S3 op)  │  Fallback on CB OPEN          │ │
│  └───────────────────────────────────────────┼───────────────────────────────┘ │
│  ┌───────────────────────────────────────────▼───────────────────────────────┐ │
│  │                       SECURITY LAYER                                       │ │
│  │   JWT Filter → Auth Provider → Password Encoder → Role-based Access      │ │
│  └───────────────────────────────────────────┬───────────────────────────────┘ │
│  ┌───────────────────────────────────────────▼───────────────────────────────┐ │
│  │                       CACHING LAYER                                       │ │
│  │   Redis (file metadata, user data, shares, dashboard)                 │ │
│  │   Per-entity TTL: FileMetadata=24h, UserCache=6h, FolderCache=2h       │ │
│  └───────────────────────────────────────────┬───────────────────────────────┘ │
│  ┌───────────────────────────────────────────▼───────────────────────────────┐ │
│  │                   PARTITIONING LAYER                                      │ │
│  │   Consistent Hash Ring (150 virtual nodes per physical node)             │ │
│  │   File metadata partitioned by: hash(orgId:userId:path)                  │ │
│  └───────────────────────────────────────────┬───────────────────────────────┘ │
└──────────────────────────────────────────────┼───────────────────────────────┘
                                                │
                    ┌───────────────────────────┼───────────────────────────┐
                    │                           │                           │
          ┌─────────▼─────────┐     ┌─────────▼─────────┐     ┌─────────▼─────────┐
          │       MySQL        │     │       Redis       │     │      AWS S3        │
          │  (Primary Store)   │     │   (Cache Layer)   │     │  (File Storage)    │
          │                     │     │                   │     │                    │
          │ • Users            │     │ • File metadata   │     │ • File blobs       │
          │ • Organizations    │     │ • Sessions        │     │ • Multipart uploads│
          │ • Files            │     │ • Shares          │     │ • Pre-signed URLs  │
          │ • Folders          │     │ • Dashboard       │     │                    │
          │ • Upload Sessions  │     │                   │     │                    │
          │ • Upload Parts     │     │                   │     │                    │
          │ • File Shares      │     │                   │     │                    │
          │ • Audit Logs       │     │                   │     │                    │
          └───────────────────┘     └───────────────────┘     └────────────────────┘
```

## 2. Consistent Hashing Ring

### Algorithm

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CONSISTENT HASH RING                                   │
│                                                                              │
│   Hash Space:  0 ────────────────────────────────────────────►  2^64-1     │
│                                                                              │
│   Physical Nodes:  node1          node2          node3                      │
│                    ┌──────┐       ┌──────┐       ┌──────┐                  │
│                    │ VN0  │       │ VN0  │       │ VN0  │  (each has 150  │
│                    │ VN1  │       │ VN1  │       │ VN1  │   virtual nodes)│
│                    │ ...  │       │ ...  │       │ ...  │                  │
│                    │ VN149│       │VN149 │       │VN149 │                  │
│                    └──┬───┘       └──┬───┘       └──┬───┘                  │
│                       │              │              │                        │
│                       └──────────────┴──────────────┘                        │
│                                    │                                          │
│                         Hash(key) ──────┼──────▼                               │
│                                    │        │                                  │
│                         ┌──────────▼────────▼──────────────────┐             │
│                         │     SortedMap<Long, String>            │             │
│                         │   TreeMap<hash, "nodeN-VNx">          │             │
│                         │                                      │             │
│                         │   ceilingEntry(hash) → node         │             │
│                         │   (clockwise search)                  │             │
│                         └──────────────────────────────────────┘             │
│                                                                              │
│   Example:                                                                  │
│     hash("org1:user1:docs/report.pdf") = 0x7FFF...  →  node2               │
│     hash("org1:user2:photos/pic.jpg")   = 0x0001...  →  node1               │
│     hash("org1:user3:music/song.mp3")   = 0xFFFF...  →  node3               │
│                                                                              │
│   Replication: getNodes(key, 2) returns 2 nearest nodes clockwise            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Adding/Removing Nodes

```
BEFORE adding node4:
  Ring: [node1: 100 VNs] [node2: 100 VNs] [node3: 100 VNs]
  Distribution: ~33% each

AFTER adding node4:
  Ring: [node1: 100 VNs] [node4: 100 VNs] [node2: 100 VNs] [node3: 100 VNs]
  Only ~25% of keys remapped (the ones between node1-node4 boundary)
  node2 and node3 unchanged → no full reshuffling
```

## 3. Resumable Upload Flow

```
┌────────┐      ┌─────────────┐      ┌─────────┐      ┌────────┐      ┌────────┐
│ Client │      │ CloudSync   │      │   S3    │      │ MySQL  │      │ Redis  │
└───┬────┘      └──────┬──────┘      └────┬────┘      └───┬────┘      └───┬────┘
    │                    │                   │              │               │
    │ 1. POST /upload/init                    │              │               │
    │   {fileName, fileSize}                  │              │               │
    │ ──────────────────► │                  │              │               │
    │                    │ 2. CreateMultipartUpload()        │               │
    │                    │ ──────────────────► │              │               │
    │                    │ ◄────────────────── │ (uploadId)  │               │
    │                    │ 3. INSERT upload_session            │               │
    │                    │ ────────────────────────────────────────────────► │
    │                    │ 4. INSERT upload_parts (N records)               │
    │                    │ ────────────────────────────────────────────────► │
    │ ◄──────────────── │ {sessionToken, uploadId, totalChunks}           │
    │                    │                   │              │               │
    │ 5. POST /upload/chunk (binary)      │              │               │
    │   X-Session-Token: xxx               │              │               │
    │   X-Part-Number: 1                  │              │               │
    │   [chunk bytes]                      │              │               │
    │ ────────────────────────────────────►│              │               │
    │                    │ 6. UploadPart(uploadId, 1, data)                │
    │                    │ ──────────────────► │              │               │
    │                    │ ◄───────────────── │ (etag)      │               │
    │                    │ 7. UPDATE part SET is_uploaded=true, etag=...  │
    │                    │ ────────────────────────────────────────────────► │
    │                    │ 8. CACHE session status                         │
    │                    │ ────────────────────────────────────────────────► │
    │ ◄────────────────────────────────────────────────────────────────── │
    │ {progress: 5%, uploadedChunks: 1, totalChunks: 20}                │
    │                    │                   │              │               │
    │ [ ... upload chunks 2, 3, 4, 5, 6 ... ]                         │
    │                    │                   │              │               │
    │                    │                   │              │               │
    │ ⚡ CONNECTION DROPS at chunk 7 (simulated failure)                 │
    │                    │                   │              │               │
    │                    │                   │              │               │
    │ 9. GET /upload/status/{sessionToken}                              │
    │ ──────────────────────────────────────────────────────────────────► │
    │ ◄────────────────────────────────────────────────────────────────── │
    │ {uploadedChunks: 6, totalChunks: 20, progress: 30%}              │
    │                    │                   │              │               │
    │ 10. GET /upload/pending/{sessionToken}  (discover what to resume)│
    │ ──────────────────────────────────────────────────────────────────► │
    │ ◄────────────────────────────────────────────────────────────────── │
    │ [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]        │
    │                    │                   │              │               │
    │ 11. Resume chunk 7 (POST /upload/chunk with X-Part-Number: 7)  │
    │ ──────────────────────────────────────────────────────────────────► │
    │ ◄────────────────────────────────────────────────────────────────── │
    │                    │                   │              │               │
    │ [ ... upload remaining chunks ... ]                                  │
    │                    │                   │              │               │
    │ 12. POST /upload/complete             │              │               │
    │    {sessionToken}                     │              │               │
    │ ──────────────────► │                  │              │               │
    │                    │ 13. ListParts() (get ETags)     │               │
    │                    │ ──────────────────► │              │               │
    │                    │ ◄───────────────── │ (etag1...etagN)              │
    │                    │ 14. CompleteMultipartUpload(parts)│              │
    │                    │ ──────────────────► │              │               │
    │                    │ ◄───────────────── │              │               │
    │                    │ 15. INSERT file_entity             │               │
    │                    │ ────────────────────────────────────────────────► │
    │                    │ 16. UPDATE user/org storage_used  │               │
    │                    │ ────────────────────────────────────────────────► │
    │                    │ 17. EVICT upload session cache                 │
    │                    │ ────────────────────────────────────────────────► │
    │ ◄──────────────── │ {fileId, downloadUrl}                          │
    │                    │                   │              │               │
    │ ✓ UPLOAD COMPLETE                                                          │
```

## 4. Circuit Breaker State Machine

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CIRCUIT BREAKER STATES                               │
│                                                                              │
│   ┌────────────────────────────────────────────────────────────────────────┐  │
│   │                         CLOSED STATE                                 │  │
│   │   (Normal Operation)                                                  │  │
│   │                                                                        │  │
│   │   All S3 requests pass through:                                       │  │
│   │   ┌──────────┐     ┌──────────┐     ┌──────────┐                   │  │
│   │   │ Request  │────►│ Circuit  │────►│   S3     │                   │  │
│   │   │          │     │ Breaker  │     │ Service  │                   │  │
│   │   └──────────┘     └────┬─────┘     └──────────┘                   │  │
│   │                          │                                            │  │
│   │            Track: success/failure counts                             │  │
│   │            Sliding window: last 10 calls                            │  │
│   │            If failure rate > 50% → transition to OPEN                │  │
│   │                          │                                            │  │
│   │                          ▼                                            │  │
│   └──────────────────────────┼─────────────────────────────────────────────┘  │
│                               │                                              │
│                               │ failureRate > 50%                             │
│                               ▼                                              │
│   ┌────────────────────────────────────────────────────────────────────────┐  │
│   │                         OPEN STATE                                    │  │
│   │   (Fail Fast)                                                        │  │
│   │                                                                        │  │
│   │   All S3 requests FAIL IMMEDIATELY:                                  │  │
│   │   ┌──────────┐     ┌──────────┐     ┌──────────────────┐            │  │
│   │   │ Request  │────►│ Circuit  │────►│ CallNotPermitted│            │  │
│   │   │          │     │ Breaker  │     │ Exception thrown │            │  │
│   │   └──────────┘     └──────────┘     └──────────────────┘            │  │
│   │                          │                                            │  │
│   │            Duration: 30 seconds (waitDurationInOpenState)             │  │
│   │            After 30s → transition to HALF_OPEN                       │  │
│   │                          │                                            │  │
│   └──────────────────────────┼─────────────────────────────────────────────┘  │
│                               │                                              │
│                               │ waitDurationInOpenState elapsed               │
│                               ▼                                              │
│   ┌────────────────────────────────────────────────────────────────────────┐  │
│   │                        HALF_OPEN STATE                                 │  │
│   │   (Testing Recovery)                                                  │  │
│   │                                                                        │  │
│   │   Allow limited requests to test if S3 is healthy:                    │  │
│   │   ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐  │  │
│   │   │ Request  │────►│ Circuit  │────►│   S3     │────►│Response │  │  │
│   │   │          │     │ Breaker  │     │ Service  │     │          │  │  │
│   │   └──────────┘     └──────────┘     └──────────┘     └──────────┘  │  │
│   │                          │                                            │  │
│   │            Permitted calls: 3 (permittedNumberOfCallsInHalfOpen)  │  │
│   │                          │                                            │  │
│   │   If 3 calls succeed  → CLOSED (recovered)                          │  │
│   │   If any call fails   → OPEN (still failing)                        │  │
│   │                          │                                            │  │
│   └──────────────────────────┼─────────────────────────────────────────────┘  │
│                               │                                              │
└───────────────────────────────┼───────────────────────────────────────────────┘
                                │
        ┌───────────────────────┴───────────────────────┐
        │                                               │
        │  3/3 succeed                                  │  1+ fail
        ▼                                               ▼
    CLOSED                                            OPEN
```

### Circuit Breaker Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      s3Operations:
        slidingWindowSize: 10          # Track last 10 calls
        minimumNumberOfCalls: 5       # Need 5 calls before evaluating
        failureRateThreshold: 50      # Open if >50% fail
        slowCallRateThreshold: 80     # Open if >80% are slow
        slowCallDurationThreshold: 5s  # "Slow" = >5 seconds
        waitDurationInOpenState: 30s   # Stay open for 30s
        permittedNumberOfCallsInHalfOpenState: 3  # Allow 3 test calls
        automaticTransitionFromOpenToHalfOpenEnabled: true

  retry:
    instances:
      s3Operations:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - software.amazon.awssdk.core.exception.SdkClientException
          - java.net.SocketTimeoutException
```

## 5. Redis Caching Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REDIS CACHE LAYER                                    │
│                                                                              │
│  Cache Name         │ TTL      │ Keys                        │ Invalidation │
│ ───────────────────┼──────────┼─────────────────────────────┼───────────────│
│  fileMetadata      │ 24 hours │ file:{id}, s3key:{s3Key}   │ On file CRUD │
│  userCache         │ 6 hours  │ user:{id}                  │ On user update│
│  organizationCache │ 12 hours │ org:{id}                    │ On org update│
│  folderCache      │ 2 hours  │ folder:{id}, path:{path}   │ On folder CRUD│
│  uploadSession     │ 30 min   │ upload:{token}            │ On complete  │
│  shareCache        │ 1 hour   │ share:{token}              │ On share CRUD│
│  dashboard         │ 10 min   │ dashboard:user:{id}        │ On file CRUD │
│                                                                              │
│  Implementation:                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  CacheManager (RedisCacheManager)                                    │   │
│  │  └── RedisTemplate<String, Object>                                    │   │
│  │      └── GenericJackson2JsonRedisSerializer                          │   │
│  │          └── JavaTimeModule (for LocalDateTime)                     │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Cache Eviction Patterns:                                                    │
│  • Write-through: Update cache on create/update                            │
│  • Evict on delete: Remove from cache immediately                           │
│  • TTL expiration: Automatic cleanup for abandoned entries                  │
│  • Pattern-based: Evict all user/org caches on deletion                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 6. Data Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ENTITY RELATIONSHIP DIAGRAM                       │
│                                                                              │
│  ┌──────────────────┐         ┌──────────────────┐                          │
│  │  Organization    │1───N───│      User        │                          │
│  │  ───────────    │         │  ───────────    │                          │
│  │  id (PK)        │         │  id (PK)        │                          │
│  │  name           │         │  email (UK)      │                          │
│  │  slug           │         │  password        │                          │
│  │  storageQuota   │         │  role            │                          │
│  │  storageUsed    │         │  orgId (FK)     │──────► Organization     │
│  │  maxUsers       │         │  storageUsed     │                          │
│  │  status         │         └────────┬─────────┘                          │
│  └──────────────────┘                  │                                     │
│           │                       1───N│───N                                 │
│           │                          │                                      │
│     1───N│                     ┌─────▼──────┐   ┌──────────────────┐       │
│  ┌────────▼───────┐            │   FileShare │   │    Folder        │       │
│  │    File       │            │  ─────────  │   │  ───────────    │       │
│  │  ───────────  │            │  id (PK)    │   │  id (PK)        │       │
│  │  id (PK)      │            │  fileId (FK)│───► File           │       │
│  │  s3Key (UK)  │◄────────────│  shareToken  │   │  parentId (FK)──┼───N   │
│  │  ownerId (FK) │             │  sharedBy   │   │  ownerId (FK)   │       │
│  │  orgId (FK)──►│Organization │  sharedWith  │   │  orgId (FK)───►│Org    │
│  │  folderId(FK)│◄───N──┐     └──────────────┘   │  folderPath     │       │
│  │  status      │       │                         └──────────────────┘       │
│  └───────────────┘       │                                                      │
│           │             │                                                      │
│           │             │                                                      │
│     ┌─────▼──────────────▼───────┐                                           │
│     │    UploadSession          │                                           │
│     │  ───────────────────────  │                                           │
│     │  id (PK)                  │                                           │
│     │  sessionToken (UK)        │                                           │
│     │  uploadId (S3)            │                                           │
│     │  totalChunks              │                                           │
│     │  uploadedChunks           │                                           │
│     │  status                   │                                           │
│     │  userId (FK)              │                                           │
│     │  destinationS3Key         │                                           │
│     └──────────────┬─────────────┘                                           │
│                    │ 1───N                                                   │
│           ┌───────▼─────────────┐                                          │
│           │    UploadPart        │                                          │
│           │  ─────────────────  │                                          │
│           │  id (PK)            │                                          │
│           │  sessionId (FK)      │                                          │
│           │  partNumber          │                                          │
│           │  etag                │                                          │
│           │  isUploaded          │                                          │
│           └─────────────────────┘                                          │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │    AuditLog                                                             │  │
│  │  ─────────                                                               │  │
│  │  id (PK) │ userId │ orgId │ action │ entityType │ entityId │ ...     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 7. JWT Authentication Flow

```
┌────────┐      ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│ Client │      │ CloudSync    │      │    MySQL    │      │    Redis    │
└───┬────┘      └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
    │                    │                    │                    │
    │ 1. POST /auth/login                    │                    │
    │   {email, password}                   │                    │
    │ ──────────────────► │                  │                    │
    │                    │ 2. Validate credentials               │
    │                    │ ──────────────────► │                  │
    │                    │ ◄────────────────── │                  │
    │                    │ 3. Generate tokens                     │
    │                    │   accessToken (15min)                 │
    │                    │   refreshToken (7 days)               │
    │                    │ 4. Store refreshToken hash           │
    │                    │ ────────────────────────────────────────────────► │
    │                    │ 5. Return tokens                     │
    │ ◄──────────────── │ {accessToken, refreshToken}         │
    │                    │                   │                    │
    │ 6. All subsequent requests                                 │
    │   Authorization: Bearer {accessToken}                      │
    │ ───────────────────────────────────────────────────────────► │
    │                    │ JWT Validation                         │
    │                    │ Extract: userId, role, orgId          │
    │                    │ Check expiration                      │
    │ ◄─────────────────────────────────────────────────────────── │
    │                    │                   │                    │
    │                    │                   │                    │
    │ 7. POST /auth/refresh                                       │
    │   {refreshToken}                                           │
    │ ───────────────────────────────────────────────────────────► │
    │                    │ Validate refresh token                 │
    │                    │ Check not expired                     │
    │                    │ Match stored hash                     │
    │                    │ ────────────────────────────────────────────────► │
    │                    │ 8. Generate NEW accessToken          │
    │                    │ 9. Rotate refreshToken              │
    │                    │ ────────────────────────────────────────────────► │
    │ ◄────────────────────────────────────────────────────────── │
    │ {newAccessToken, newRefreshToken}                          │
```

## 8. Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PRODUCTION DEPLOYMENT                              │
│                                                                              │
│                              ┌─────────────┐                                 │
│                              │   Route53   │                                 │
│                              │  (DNS/CDN)  │                                 │
│                              └──────┬──────┘                                 │
│                                     │                                         │
│                              ┌──────▼──────┐                                 │
│                              │ AWS CloudFront│                                │
│                              │  (CDN/WAF)  │                                 │
│                              └──────┬──────┘                                 │
│                                     │                                         │
│                         ┌───────────▼───────────┐                            │
│                         │   AWS Application    │                            │
│                         │      Load Balancer    │                            │
│                         └───────────┬───────────┘                            │
│                                     │                                         │
│               ┌─────────────────────┼─────────────────────┐                │
│               │                     │                     │                │
│          ┌────▼────┐          ┌────▼────┐          ┌────▼────┐               │
│          │ Instance │          │ Instance │          │ Instance │               │
│          │    1     │          │    2     │          │    3     │               │
│          │ (EC2/ECS)│          │ (EC2/ECS)│          │ (EC2/ECS)│               │
│          └────┬────┘          └────┬────┘          └────┬────┘               │
│               │                     │                     │                   │
│               └─────────────────────┼─────────────────────┘                   │
│                                     │                                         │
│                    ┌────────────────┬┴────────────────┐                       │
│                    │                │                 │                       │
│               ┌─────▼─────┐   ┌──────▼──────┐   ┌──────▼──────┐               │
│               │   MySQL   │   │   Redis    │   │   AWS S3   │               │
│               │   (RDS)   │   │(ElastiCache)│   │   Bucket   │               │
│               │  Multi-AZ │   │  Cluster   │   │  (Files)   │               │
│               └───────────┘   └───────────┘   └────────────┘               │
└─────────────────────────────────────────────────────────────────────────────┘

Container Orchestration (Docker Swarm / Kubernetes):
  • Auto-scaling based on CPU/memory usage
  • Rolling deployments with health checks
  • Service mesh for inter-service communication
  • Centralized logging (CloudWatch/Graylog)
  • Metrics collection (Prometheus/Grafana)
```

## 9. API Request/Response Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        TYPICAL API REQUEST FLOW                              │
│                                                                              │
│  Request:                                                                   │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │  POST /api/files/{fileId}/download                                   │  │
│  │  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...                        │  │
│  │  Content-Type: application/json                                       │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                       │
│                                      ▼                                       │
│  1. JwtAuthenticationFilter                                                         │
│     • Extract JWT from header                                              │
│     • Validate signature & expiration                                       │
│     • Extract userId, role, orgId                                          │
│     • Set SecurityContext                                                  │
│                                      │                                       │
│                                      ▼                                       │
│  2. Controller                                                             │
│     • Map @PathVariable, @RequestBody to DTOs                              │
│     • Validate with @Valid                                                 │
│                                      │                                       │
│                                      ▼                                       │
│  3. Service Layer                                                          │
│     • Check cache (Redis) ─┐                                              │
│     │  HIT → return cached  │                                              │
│     │  MISS ──────────────┘│                                              │
│     │                      ▼                                               │
│     • Check authorization (owner or shared)                                │
│     • Execute business logic                                               │
│     • Update cache (if write operation)                                    │
│                                      │                                       │
│                                      ▼                                       │
│  4. Resilience Decorator                                                  │
│     • Circuit breaker check (fail-fast if OPEN)                            │
│     • Retry with exponential backoff (3 attempts)                         │
│     • Timeout protection (30s)                                            │
│                                      │                                       │
│                                      ▼                                       │
│  5. S3 Service                                                            │
│     • Execute S3 operation                                                 │
│     • On failure: retry → circuit breaker may open                        │
│                                      │                                       │
│                                      ▼                                       │
│  6. Response                                                              │
│     ┌────────────────────────────────────────────────────────────────┐     │
│     │  HTTP/1.1 200 OK                                               │     │
│     │  Content-Type: application/json                                 │     │
│     │  X-Content-Type-Options: nosniff                               │     │
│     │  Cache-Control: no-store                                        │     │
│     │  {                                                             │     │
│     │    "success": true,                                            │     │
│     │    "message": "File retrieved successfully",                    │     │
│     │    "data": { ... file metadata ... },                          │     │
│     │    "timestamp": "2026-04-07T12:30:00"                         │     │
│     │  }                                                             │     │
│     └────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  Error Responses:                                                           │
│  ┌────────────────────────────────────────────────────────────────┐        │
│  │  401 Unauthorized     → Invalid/missing JWT                     │        │
│  │  403 Forbidden        → No permission to access resource          │        │
│  │  404 Not Found        → File/user/organization not found         │        │
│  │  409 Conflict          → Duplicate resource                       │        │
│  │  413 Payload Too Large→ File exceeds size limit                  │        │
│  │  429 Too Many Requests→ Rate limit exceeded                      │        │
│  │  503 Service Unavailable→ Circuit breaker OPEN                    │        │
│  │  507 Insufficient Storage→ Storage quota exceeded                  │        │
│  └────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 10. Technology Stack Summary

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Runtime** | Java 17 | LTS with modern features |
| **Framework** | Spring Boot 3.2 | Web, Data, Security, Cache |
| **Database** | MySQL 8.x | Primary data store with ACID |
| **Cache** | Redis 7 | Metadata caching, sessions |
| **Storage** | AWS S3 | File blob storage |
| **Resilience** | Resilience4j | Circuit breaker, retry, timeout |
| **Auth** | JWT (jjwt) | Stateless authentication |
| **API Docs** | OpenAPI 3.0 | Interactive documentation |
| **Build** | Maven 3.9 | Dependency management |
| **Container** | Docker | Deployment packaging |
| **Orchestration** | Docker Compose | Local dev orchestration |
