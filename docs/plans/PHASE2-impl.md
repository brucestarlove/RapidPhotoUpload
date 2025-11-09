## Phase 2 implementation complete

### 1. Domain model
- `UploadJob` aggregate with status tracking and progress updates
- `Photo` aggregate with lifecycle management
- `UploadJobStatus` and `PhotoStatus` enums
- Domain repositories: `UploadJobRepository`, `PhotoRepository`

### 2. Domain events
- `UploadJobCreated` event
- `PhotoQueued` event

### 3. Transactional Outbox
- `OutboxEvent` entity for reliable event publishing
- `OutboxEventRepository` with queries for unprocessed events
- `OutboxService` for publishing events atomically with aggregates

### 4. S3 presigner services
- `S3PresignService` for single-part presigned PUT URLs
- `S3MultipartPresignService` for multipart uploads (>5MB) with presigned part URLs

### 5. API DTOs
- `CreateUploadJobRequest` with validation
- `FileUploadRequest` with filename, MIME type, and size validation
- `UploadStrategy` enum (S3_PRESIGNED, S3_MULTIPART)
- `CreateUploadJobResponse` with job ID and upload items
- `PhotoUploadItem` for single-part and multipart uploads
- `MultipartUploadInfo` and `PartUrl` for multipart details
- `UpdateProgressRequest` for progress tracking

### 6. Application handlers
- `CreateUploadJobHandler` creates jobs, generates presigned URLs, and publishes events
- `UpdateProgressHandler` handles best-effort progress updates

### 7. Infrastructure
- `JpaUploadJobRepository` with `findByIdWithPhotos` query
- `JpaPhotoRepository` with job and S3 key lookups

### 8. Controller
- `UploadPhotoController` with:
  - `POST /commands/upload-jobs` — creates batch upload jobs
  - `POST /commands/upload/progress` — updates upload progress

### Security
- Endpoints require `photos:write` authority (configured in `SecurityConfig`)
- User ownership validation in handlers

### Notes
- Multipart uploads use `S3Client` to create the upload, then presign individual parts
- S3 keys follow pattern: `${env}/${userId}/${jobId}/${photoId}.ext`
- Events are published via Transactional Outbox for reliability
- Database migrations are already in place

Ready for testing. The implementation follows the Phase 2 specification and integrates with the existing Phase 1 foundation.
