# Backend PRD — RapidPhotoUpload (Spring Boot · AWS S3 · PostgreSQL)

> Scope: design a high-volume, *asynchronous* media upload backend that can handle ~100 concurrent uploads with real-time status, clean separation of reads/writes (CQRS), DDD domain modeling, and Vertical Slice Architecture. Web + React Native clients share the same APIs. 

---

## 1) Goals & Non-Goals

### Goals

* **Handle 100 concurrent uploads per user session** with a responsive, non-blocking experience and visible **per-file and batch progress**. 
* **Robust status tracking** (Queued → Uploading → Processing → Completed/Failed/Cancelled).
* **Durable storage** of binaries in **AWS S3** (multipart uploads) and metadata in **PostgreSQL**.
* **Clear DDD + CQRS + VSA**: feature-centric code organization, rich domain objects, separate write/read paths.
* **Basic authentication** (JWT over Basic) for web and mobile, ready for future OIDC. 

### Non-Goals

* Heavy image processing/ML (beyond basic thumbnailing + metadata extraction).
* Full RBAC/tenancy (defer to Phase 2).
* Advanced search (Phase 2: OpenSearch/pgvector).

---

## 2) High-Level Architecture

### Components

* **API Gateway / Spring Boot app**

  * **Command API** (writes): create upload jobs, generate presigned URLs, finalize uploads.
  * **Query API** (reads): poll/get status, list photos, filter/tag metadata.
  * **Query API for polling** progress status.
* **AWS S3**: object store using **Multipart Upload** + **S3 Events** (ObjectCreated).
* **SQS**: internal work queue for post-upload processing (thumbnails, EXIF, virus scan placeholder).
* **PostgreSQL**: source of truth for **Photo**, **UploadJob**, **UploadPart**, **Tag**, **User**.
* **Outbox & Idempotency**: reliable command handling and event publication.
* **CloudWatch/X-Ray**: tracing/metrics/logs; **Prometheus** (via Micrometer) + **Grafana** optional.

### Core Flows (happy path)

1. **Client requests batch** → Command API creates an **UploadJob** + N **Photo** records in *Queued*.
2. API returns **presigned URLs** (one per file) + **multipart upload IDs** (optional path).
3. **Client uploads directly to S3** (bypasses app CPU/RAM), emits progress (per part) to UI; also **POSTs progress ticks** to `/commands/upload/progress` (best-effort) to enrich server-side telemetry.
4. **S3 ObjectCreated event** → **EventBridge → SQS** → **Processor** marks each Photo *Processing*, extracts EXIF, generates thumbnails, calculates checksums, stores **object key/ETag/size**.
5. Processor marks Photo *Completed* or *Failed*; **Client polls Query API** for updates; Query API reflects final status.
6. Batch completes when all Photos are terminal (*Completed/Failed/Cancelled*).

> Rationale: direct-to-S3 + presigned URLs minimizes server load while enabling precise progress UX; SQS decouples post-upload work for reliability under burst concurrency.

---

## 3) Domain-Driven Design (Ubiquitous Language)

**Aggregates / Entities**

* **User**(userId, email, passwordHash, status)
* **UploadJob**(jobId, userId, totalCount, completedCount, failedCount, status, createdAt)
* **Photo**(photoId, jobId, userId, filename, mimeType, bytes, s3Key, eTag, checksum, width, height, exifJson, status, createdAt, completedAt)
* **UploadPart**(photoId, partNo, bytes, status, updatedAt) — optional if tracking multipart detail
* **Tag**(tagId, userId, label); **PhotoTag**(photoId, tagId)

**Value Objects**

* **Progress**(percent, bytesSent, bytesTotal)
* **ObjectRef**(bucket, key, region)
* **Checksum**(algo, value)

**Domain Events**

* `UploadJobCreated`, `PhotoQueued`, `PhotoUploadStarted`, `PhotoUploadCompleted`, `PhotoProcessingCompleted`, `PhotoFailed`, `UploadJobCompleted`.

**Invariants**

* `UploadJob.status` is terminal iff all `Photo.status` are terminal.
* A `Photo` is *Completed* only after object is present in S3 **and** metadata processing succeeds.

---

## 4) CQRS & Vertical Slice Architecture

### Packages (example)

```
com.starscape.rapidupload
└── features
    ├── uploadphoto        // Write side (commands)
    │   ├── api            // controllers for command endpoints
    │   ├── app            // command handlers, orchestrators
    │   ├── domain         // aggregates, repos (interfaces), events
    │   └── infra          // S3, SQS, JPA impls, outbox
    ├── getphotometadata   // Read side (queries)
    ├── listphotos         // Read side
    ├── trackprogress      // WS/SSE + progress projections
    └── auth
```

### Command API (writes)

* `POST /commands/upload-jobs`

  * body: `{files:[{filename,mimeType,bytes}], strategy:"s3-presigned"}`
  * returns: `{jobId, items:[{photoId, presignedUrl, multipart?:{uploadId,partSize}}]}`
* `POST /commands/upload/progress`

  * body: `{photoId, bytesSent, bytesTotal}` (best-effort telemetry)
* `POST /commands/upload/{photoId}/finalize` *(optional if relying solely on S3 events)*
* `POST /commands/photos/{photoId}/tags` → add tags
* `DELETE /commands/photos/{photoId}` → soft delete/cancel if still uploading

### Query API (reads)

* `GET /queries/upload-jobs/{jobId}` → job status snapshot (+ per-item)
* `GET /queries/photos/{photoId}` → metadata
* `GET /queries/photos?tag=&q=&page=&size=` → list/filter
* `GET /queries/photos/{photoId}/download-url` → **time-boxed presigned GET**

### Progress Tracking

* **Polling** - Client polls `GET /queries/upload-jobs/{jobId}` every 1-2 seconds
* Server returns job status with all photo statuses
* Recommended polling interval: 1500ms during active uploads
* Stop polling when job status is COMPLETED or FAILED

---

## 5) Storage & Reliability

### AWS S3

* **Multipart Upload** for files > 5MB; client chooses `partSize` (e.g., 8–16 MB) to balance progress granularity.
* Server generates **presigned PUT** (or `createMultipartUpload` + per-part presigned URLs).
* **S3 Lifecycle** rules optional (e.g., move originals to IA after 30d).

### PostgreSQL

* Tables: `users`, `upload_jobs`, `photos`, `photo_tags`, `tags`, `upload_parts`, `outbox_events`.
* **Indexes**: `(user_id, created_at)` on `photos`; `(job_id)`; GIN on `tags(label)` if searching.
* **Migrations**: Flyway.

### Message & Processing

* **S3 → EventBridge → SQS** fan-in to **processor worker** (Spring Boot).
* Processing steps:

  1. HEAD/GET metadata, compute checksum if client didn’t.
  2. Extract **EXIF** (metadata library).
  3. Generate **thumbnail(s)** (e.g., 256px, 1024px) → write to `thumbnails/` prefix.
  4. Update `photos` row; publish `PhotoProcessingCompleted`.
* **Retries**: SQS redrive policy + Dead Letter Queue.
* **Idempotency**: idempotency key = `(photoId, objectETag)`; outbox to publish events reliably.
* **Consistency**: **Transactional Outbox** (JPA) + scheduled **outbox relayer**.

---

## 6) Security & Auth

* **JWT (HS256/RS256)** issued by Auth slice (username/password for now).
* **Scopes**: `photos:write`, `photos:read`.
* **S3 access**: backend IAM role with policy restricted to the app bucket prefix `${env}/${userId}/*`.
* **Uploads**: clients use **presigned URLs** only; server never handles raw bytes directly (except small files during tests).
* **Validation**: filename allow-list, MIME sniffing, size limits, image dimension limits.
* **Virus scan (placeholder)**: hook for future ClamAV/Lambda.

---

## 7) Performance, SLAs, and Limits

* **Target**: 100 files (~2 MB avg) concurrently per session without API degradation; batch completes ≤ 90s on standard broadband when network permits. 
* **API timeouts**: 30s on create-job; progress endpoints < 200ms p95.
* **Rate limits**: 10 create-job/min/user; 20 progress posts/sec/user (token bucket).
* **Backpressure**: if SQS depth > threshold or DB connection pool saturation → throttle presign issuance.

---

## 8) Observability

* **Metrics** (Micrometer):

  * `upload_job_created_total`, `photo_completed_total`, `photo_failed_total`
  * `upload_inflight_gauge`, `sqs_lag_gauge`, `thumbnail_latency_ms_histogram`
* **Tracing**: OpenTelemetry + AWS X-Ray (end-to-end spans: create-job → S3 event → processor).
* **Logging**: JSON logs with correlation IDs (`jobId`, `photoId`, `traceId`).
* **Dashboards**: “Upload Health”, “Processor Throughput”, “Error/Fail Rate”.

---

## 9) API Contracts (abridged)

**Create Upload Job**

```http
POST /commands/upload-jobs
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "files": [
    {"filename":"IMG_001.jpg","mimeType":"image/jpeg","bytes":2457811},
    {"filename":"IMG_002.png","mimeType":"image/png","bytes":5123456}
  ],
  "strategy": "s3-presigned"
}
```

**201**

```json
{
  "jobId":"job_01HZW...",
  "items":[
    {
      "photoId":"ph_01HZ...",
      "method":"PUT",
      "presignedUrl":"https://s3...",
      "multipart":null
    }
  ]
}
```

**Realtime Progress (server → client)**

```json
{"jobId":"job_01HZW...","photoId":"ph_01HZ...","status":"Processing","progress":100}
```

---

## 10) Data Model (DDL excerpt)

```sql
create table upload_jobs (
  job_id varchar primary key,
  user_id varchar not null,
  total_count int not null,
  completed_count int not null default 0,
  failed_count int not null default 0,
  status varchar not null,            -- QUEUED | IN_PROGRESS | COMPLETED | FAILED | CANCELLED
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table photos (
  photo_id varchar primary key,
  job_id varchar not null references upload_jobs(job_id),
  user_id varchar not null,
  filename text not null,
  mime_type text not null,
  bytes bigint not null,
  s3_key text,
  etag text,
  checksum text,
  width int, height int,
  exif_json jsonb,
  status varchar not null,            -- QUEUED | UPLOADING | PROCESSING | COMPLETED | FAILED | CANCELLED
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
```

---

## 11) Vertical Slices — Implementation Notes

* **UploadPhotoSlice**

  * *CommandHandler*: validates batch, allocates IDs, writes `UploadJob` + `Photo` rows, produces presigned URLs, emits `UploadJobCreated`.
  * *Infra*: S3 client, presign service; Outbox repository.

* **TrackProgressSlice**

  * *WS/SSE Hub*: topics per `jobId` (and per `userId`).
  * *Projection*: listens to domain events, updates `UploadJob.completedCount` etc., pushes updates.

* **GetPhotoMetadataSlice / ListPhotosSlice**

  * *QueryHandlers*: read-optimized JPA/SQL (can use lightweight projections), optional caching.

* **ProcessorSlice**

  * *SQSListener*: for S3 `ObjectCreated:*`; loads `Photo`, performs metadata/thumbnail, transitions state.

---

## 12) Failure Handling & Idempotency

* **Client retries**: presigned URL expiration handled (short TTL, renew via `POST /commands/upload/{photoId}/renew`)
* **Server retries**: SQS redrives with exponential backoff; idempotent state transitions using `(photoId, newStatus)` guard.
* **Partial failures**: batch finishes with `status=COMPLETED_WITH_ERRORS`; final counts reflected.
* **Cancellation**: user can cancel a photo (abort multipart on S3) → status `CANCELLED`.

---

## 13) Security, Privacy, and Compliance

* **PII minimization**: only email + userId; filenames treated as user content.
* **Object ACLs**: private; all access via presigned GET.
* **At-rest encryption**: S3 SSE-S3 (or SSE-KMS), RDS encryption.
* **In-transit**: HTTPS only.
* **Audit log**: append-only table for `userId`, `action`, `photoId`, `ts`.

---

## 14) Acceptance Criteria

1. Creating a batch returns presigned URLs for **N files** in < 500 ms p95.
2. Uploading 100 images concurrently (2–6 MB each) completes with *no API timeouts*; UI remains interactive; backend CPU stays < 70% and DB < 60% under test load. 
3. Each photo transitions through statuses; **WebSocket/SSE** delivers progress updates; **Query API** reflects same within 1s.
4. Thumbnails + EXIF visible via Query API for completed items; downloads served via presigned GET.
5. All flows covered by **integration tests** (create job → upload → S3 event → processor → status complete). 

---

## 15) Testing Strategy

* **Unit**: domain aggregate invariants; presign service.
* **Contract**: REST endpoints with Spring Cloud Contract.
* **Integration/E2E**:

  * Testcontainers (Postgres + LocalStack S3/SQS).
  * Simulate 100 concurrent multipart PUTs; assert events, DB rows, thumbnails produced, and job completion.
* **Load**: k6/Gatling profile for presign, progress posts, and processor throughput.

---

## 16) Deployment & DevOps

* **Infra as Code**: Terraform or CDK (VPC, RDS Postgres, S3 bucket, SQS, EventBridge rule, IAM).
* **CI/CD**: GitHub Actions → build, run tests, containerize; deploy to ECS Fargate or EKS; blue/green or rolling.
* **Config**: Spring Cloud Config/SSM Parameter Store; secrets via Secrets Manager.
* **Zero-downtime**: health probes, graceful shutdown of WS connections, sticky sessions not required (SSE/WS behind ALB with target group).

---

## 17) Future Extensions

* **Resumable uploads (Tus)** for flaky mobile networks.
* **Content moderation** hooks.
* **Search**: tags + EXIF fields + embeddings.
* **Team spaces** and share links.
* **Signed WebUpload tokens** for kiosk devices.

---

### Notes

* This PRD aligns with the assessment brief’s functional and architectural requirements (concurrency, async UX, real-time status, web + mobile, DDD/CQRS/VSA). 
