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


---

# Testing Guide for Phase 2: Core Upload Flow

This guide covers testing strategies for Phase 2 implementation, including unit tests, integration tests, and manual testing procedures.

---

## Prerequisites

### Test Dependencies

Add these to `pom.xml` if not already present:

```xml
<!-- Testcontainers for integration tests -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>localstack</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- REST Assured for API testing -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.4.0</version>
    <scope>test</scope>
</dependency>

<!-- Awaitility for async testing -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
</dependency>
```

---

## 1. Unit Tests

### Domain Model Tests

**`src/test/java/com/starscape/rapidupload/features/uploadphoto/domain/UploadJobTest.java`**

```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.features.uploadphoto.domain.events.UploadJobCreated;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadJobTest {
    
    @Test
    void shouldCreateJobWithQueuedStatus() {
        UploadJob job = new UploadJob("job_123", "user_456", 5);
        
        assertThat(job.getJobId()).isEqualTo("job_123");
        assertThat(job.getUserId()).isEqualTo("user_456");
        assertThat(job.getTotalCount()).isEqualTo(5);
        assertThat(job.getStatus()).isEqualTo(UploadJobStatus.QUEUED);
        assertThat(job.getCompletedCount()).isEqualTo(0);
        assertThat(job.getFailedCount()).isEqualTo(0);
    }
    
    @Test
    void shouldRegisterUploadJobCreatedEvent() {
        UploadJob job = new UploadJob("job_123", "user_456", 3);
        
        assertThat(job.getDomainEvents()).hasSize(1);
        assertThat(job.getDomainEvents().get(0))
            .isInstanceOf(UploadJobCreated.class);
        
        UploadJobCreated event = (UploadJobCreated) job.getDomainEvents().get(0);
        assertThat(event.jobId()).isEqualTo("job_123");
        assertThat(event.userId()).isEqualTo("user_456");
        assertThat(event.totalCount()).isEqualTo(3);
    }
    
    @Test
    void shouldUpdateProgressWhenPhotosComplete() {
        UploadJob job = new UploadJob("job_123", "user_456", 3);
        
        Photo photo1 = new Photo("ph_1", "user_456", "test.jpg", "image/jpeg", 1024);
        Photo photo2 = new Photo("ph_2", "user_456", "test2.jpg", "image/jpeg", 2048);
        Photo photo3 = new Photo("ph_3", "user_456", "test3.jpg", "image/jpeg", 3072);
        
        job.addPhoto(photo1);
        job.addPhoto(photo2);
        job.addPhoto(photo3);
        
        photo1.markUploading();
        photo1.markProcessing("key1", "bucket", "etag1");
        photo1.markCompleted(1920, 1080, "{}", "checksum1");
        
        photo2.markUploading();
        photo2.markProcessing("key2", "bucket", "etag2");
        photo2.markCompleted(1920, 1080, "{}", "checksum2");
        
        photo3.markFailed("Upload failed");
        
        job.updateProgress();
        
        assertThat(job.getCompletedCount()).isEqualTo(2);
        assertThat(job.getFailedCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(UploadJobStatus.COMPLETED_WITH_ERRORS);
    }
    
    @Test
    void shouldTransitionToCompletedWhenAllPhotosSucceed() {
        UploadJob job = new UploadJob("job_123", "user_456", 2);
        
        Photo photo1 = new Photo("ph_1", "user_456", "test.jpg", "image/jpeg", 1024);
        Photo photo2 = new Photo("ph_2", "user_456", "test2.jpg", "image/jpeg", 2048);
        
        job.addPhoto(photo1);
        job.addPhoto(photo2);
        
        photo1.markUploading();
        photo1.markProcessing("key1", "bucket", "etag1");
        photo1.markCompleted(1920, 1080, "{}", "checksum1");
        
        photo2.markUploading();
        photo2.markProcessing("key2", "bucket", "etag2");
        photo2.markCompleted(1920, 1080, "{}", "checksum2");
        
        job.updateProgress();
        
        assertThat(job.getStatus()).isEqualTo(UploadJobStatus.COMPLETED);
    }
}
```

**`src/test/java/com/starscape/rapidupload/features/uploadphoto/domain/PhotoTest.java`**

```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoQueued;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoTest {
    
    @Test
    void shouldCreatePhotoWithQueuedStatus() {
        Photo photo = new Photo("ph_123", "user_456", "test.jpg", "image/jpeg", 1024);
        
        assertThat(photo.getPhotoId()).isEqualTo("ph_123");
        assertThat(photo.getUserId()).isEqualTo("user_456");
        assertThat(photo.getFilename()).isEqualTo("test.jpg");
        assertThat(photo.getMimeType()).isEqualTo("image/jpeg");
        assertThat(photo.getBytes()).isEqualTo(1024);
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.QUEUED);
    }
    
    @Test
    void shouldRegisterPhotoQueuedEvent() {
        Photo photo = new Photo("ph_123", "user_456", "test.jpg", "image/jpeg", 1024);
        
        assertThat(photo.getDomainEvents()).hasSize(1);
        assertThat(photo.getDomainEvents().get(0))
            .isInstanceOf(PhotoQueued.class);
        
        PhotoQueued event = (PhotoQueued) photo.getDomainEvents().get(0);
        assertThat(event.photoId()).isEqualTo("ph_123");
        assertThat(event.filename()).isEqualTo("test.jpg");
    }
    
    @Test
    void shouldRejectInvalidInput() {
        assertThatThrownBy(() -> new Photo(null, "user_456", "test.jpg", "image/jpeg", 1024))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo ID cannot be blank");
        
        assertThatThrownBy(() -> new Photo("ph_123", null, "test.jpg", "image/jpeg", 1024))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User ID cannot be blank");
        
        assertThatThrownBy(() -> new Photo("ph_123", "user_456", "test.jpg", "image/jpeg", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bytes must be positive");
    }
    
    @Test
    void shouldTransitionThroughStatuses() {
        Photo photo = new Photo("ph_123", "user_456", "test.jpg", "image/jpeg", 1024);
        
        photo.markUploading();
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.UPLOADING);
        
        photo.markProcessing("s3/key", "bucket", "etag123");
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PROCESSING);
        assertThat(photo.getS3Key()).isEqualTo("s3/key");
        assertThat(photo.getEtag()).isEqualTo("etag123");
        
        photo.markCompleted(1920, 1080, "{\"camera\":\"Canon\"}", "sha256hash");
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.COMPLETED);
        assertThat(photo.getWidth()).isEqualTo(1920);
        assertThat(photo.getHeight()).isEqualTo(1080);
        assertThat(photo.getExifJson()).contains("Canon");
        assertThat(photo.getChecksum()).isEqualTo("sha256hash");
        assertThat(photo.getCompletedAt()).isNotNull();
    }
    
    @Test
    void shouldHandleFailure() {
        Photo photo = new Photo("ph_123", "user_456", "test.jpg", "image/jpeg", 1024);
        
        photo.markFailed("Network error");
        
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.FAILED);
        assertThat(photo.getErrorMessage()).isEqualTo("Network error");
        assertThat(photo.getCompletedAt()).isNotNull();
    }
}
```

### Service Tests

**`src/test/java/com/starscape/rapidupload/features/uploadphoto/infra/S3PresignServiceTest.java`**

```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Testcontainers
class S3PresignServiceTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(S3);
    
    private S3PresignService s3PresignService;
    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private String bucket = "test-bucket";
    
    @BeforeEach
    void setUp() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create("test", "test");
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        Region region = Region.of(localstack.getRegion());
        
        s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(S3))
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        
        s3Presigner = S3Presigner.builder()
                .endpointOverride(localstack.getEndpointOverride(S3))
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        
        // Create test bucket
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        
        s3PresignService = new S3PresignService(s3Presigner, bucket, 15);
    }
    
    @Test
    void shouldGeneratePresignedUrl() {
        S3PresignService.PresignedUploadUrl result = s3PresignService.generatePresignedPutUrl(
            "test/key.jpg",
            "image/jpeg",
            1024
        );
        
        assertThat(result.url()).isNotBlank();
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.bucket()).isEqualTo(bucket);
        assertThat(result.key()).isEqualTo("test/key.jpg");
        assertThat(result.expiresInSeconds()).isEqualTo(15 * 60);
        assertThat(result.url()).contains("test/key.jpg");
    }
}
```

---

## 2. Integration Tests

### API Integration Test

**`src/test/java/com/starscape/rapidupload/features/uploadphoto/api/UploadPhotoControllerIntegrationTest.java`**

```java
package com.starscape.rapidupload.features.uploadphoto.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.features.uploadphoto.api.dto.CreateUploadJobRequest;
import com.starscape.rapidupload.features.uploadphoto.api.dto.FileUploadRequest;
import com.starscape.rapidupload.features.uploadphoto.api.dto.UploadStrategy;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UploadPhotoControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(S3);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.s3.bucket", () -> "test-bucket");
        registry.add("aws.s3.presign-duration-minutes", () -> "15");
        registry.add("spring.profiles.active", () -> "test");
    }
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private UploadJobRepository uploadJobRepository;
    
    @Autowired
    private PhotoRepository photoRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String authToken;
    private String userId;
    
    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        
        // Register user and get JWT token
        Map<String, String> registerRequest = Map.of(
            "email", "test@example.com",
            "password", "password123"
        );
        
        Map<String, Object> registerResponse = given()
                .contentType(ContentType.JSON)
                .body(registerRequest)
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .as(Map.class);
        
        authToken = (String) registerResponse.get("token");
        userId = (String) registerResponse.get("userId");
    }
    
    @Test
    void shouldCreateUploadJobWithPresignedUrls() {
        CreateUploadJobRequest request = new CreateUploadJobRequest(
            List.of(
                new FileUploadRequest("photo1.jpg", "image/jpeg", 1024 * 1024),
                new FileUploadRequest("photo2.png", "image/png", 2048 * 1024)
            ),
            UploadStrategy.S3_PRESIGNED
        );
        
        Map<String, Object> response = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(201)
                .body("jobId", notNullValue())
                .body("items", hasSize(2))
                .body("items[0].photoId", notNullValue())
                .body("items[0].method", equalTo("PUT"))
                .body("items[0].presignedUrl", notNullValue())
                .extract()
                .as(Map.class);
        
        String jobId = (String) response.get("jobId");
        
        // Verify job persisted
        var job = uploadJobRepository.findById(jobId);
        assertThat(job).isPresent();
        assertThat(job.get().getTotalCount()).isEqualTo(2);
        assertThat(job.get().getUserId()).isEqualTo(userId);
        
        // Verify photos persisted
        var photos = photoRepository.findByJobId(jobId);
        assertThat(photos).hasSize(2);
    }
    
    @Test
    void shouldRejectInvalidFileRequest() {
        CreateUploadJobRequest request = new CreateUploadJobRequest(
            List.of(
                new FileUploadRequest("invalid.exe", "application/x-executable", 1024)
            ),
            UploadStrategy.S3_PRESIGNED
        );
        
        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(400);
    }
    
    @Test
    void shouldRejectOversizedFile() {
        CreateUploadJobRequest request = new CreateUploadJobRequest(
            List.of(
                new FileUploadRequest("huge.jpg", "image/jpeg", 100 * 1024 * 1024) // 100MB
            ),
            UploadStrategy.S3_PRESIGNED
        );
        
        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(request)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(400);
    }
    
    @Test
    void shouldUpdateProgress() {
        // First create a job
        CreateUploadJobRequest createRequest = new CreateUploadJobRequest(
            List.of(
                new FileUploadRequest("photo1.jpg", "image/jpeg", 1024 * 1024)
            ),
            UploadStrategy.S3_PRESIGNED
        );
        
        Map<String, Object> createResponse = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createRequest)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(201)
                .extract()
                .as(Map.class);
        
        List<Map<String, Object>> items = (List<Map<String, Object>>) createResponse.get("items");
        String photoId = (String) items.get(0).get("photoId");
        
        // Update progress
        Map<String, Object> progressRequest = Map.of(
            "photoId", photoId,
            "bytesSent", 512 * 1024,
            "bytesTotal", 1024 * 1024,
            "percent", 50
        );
        
        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(progressRequest)
                .post("/commands/upload/progress")
                .then()
                .statusCode(202);
        
        // Verify photo status updated
        var photo = photoRepository.findById(photoId);
        assertThat(photo).isPresent();
        assertThat(photo.get().getStatus()).isEqualTo(com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus.UPLOADING);
    }
    
    @Test
    void shouldRequireAuthentication() {
        CreateUploadJobRequest request = new CreateUploadJobRequest(
            List.of(
                new FileUploadRequest("photo1.jpg", "image/jpeg", 1024)
            ),
            UploadStrategy.S3_PRESIGNED
        );
        
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(401);
    }
}
```

---

## 3. Manual Testing

### Prerequisites

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Create test images** (optional but recommended for full testing):
   ```bash
   ./create-test-images.sh
   ```
   This creates sample images in `./test-images/`:
   - `photo1.jpg` (~1MB)
   - `photo2.png` (~2MB)
   - `large-photo.jpg` (~10MB for multipart testing)
   
   **Note:** Requires ImageMagick (`brew install imagemagick`) or Python PIL (`pip3 install Pillow`)

3. **Ensure AWS credentials are configured** (for real S3) or use LocalStack for local testing

4. **Get a JWT token:**
   ```bash
   # Register a user
   curl -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{
       "email": "test@example.com",
       "password": "password123"
     }'
   
   # Login
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "email": "test@example.com",
       "password": "password123"
     }'
   ```

### Test Scenarios

#### 1. Create Upload Job (Single-Part)

```bash
curl -X POST http://localhost:8080/commands/upload-jobs \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {
        "filename": "photo1.jpg",
        "mimeType": "image/jpeg",
        "bytes": 1048576
      },
      {
        "filename": "photo2.png",
        "mimeType": "image/png",
        "bytes": 2097152
      }
    ],
    "strategy": "S3_PRESIGNED"
  }'
```

**Expected Response:**
```json
{
  "jobId": "job_abc123...",
  "items": [
    {
      "photoId": "ph_xyz789...",
      "method": "PUT",
      "presignedUrl": "https://s3.amazonaws.com/bucket/key?X-Amz-Signature=...",
      "multipart": null
    },
    {
      "photoId": "ph_def456...",
      "method": "PUT",
      "presignedUrl": "https://s3.amazonaws.com/bucket/key?X-Amz-Signature=...",
      "multipart": null
    }
  ]
}
```

#### 2. Create Upload Job (Multipart for Large File)

```bash
curl -X POST http://localhost:8080/commands/upload-jobs \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "files": [
      {
        "filename": "large-photo.jpg",
        "mimeType": "image/jpeg",
        "bytes": 10485760
      }
    ],
    "strategy": "S3_MULTIPART"
  }'
```

**Expected Response:**
```json
{
  "jobId": "job_abc123...",
  "items": [
    {
      "photoId": "ph_xyz789...",
      "method": "MULTIPART",
      "presignedUrl": null,
      "multipart": {
        "uploadId": "upload-id-from-s3",
        "partSize": 8388608,
        "parts": [
          {
            "partNumber": 1,
            "url": "https://s3.amazonaws.com/bucket/key?uploadId=...",
            "size": 8388608
          },
          {
            "partNumber": 2,
            "url": "https://s3.amazonaws.com/bucket/key?uploadId=...",
            "size": 2097152
          }
        ]
      }
    }
  ]
}
```

#### 3. Upload File to S3 Using Presigned URL

**Option A: Using test images (if you ran `./create-test-images.sh`):**
```bash
# Use the presignedUrl from the response above
curl -X PUT "PRESIGNED_URL_HERE" \
  -H "Content-Type: image/jpeg" \
  --data-binary @./test-images/photo1.jpg
```

**Option B: Using your own image:**
```bash
curl -X PUT "PRESIGNED_URL_HERE" \
  -H "Content-Type: image/jpeg" \
  --data-binary @/path/to/your/photo.jpg
```

**Expected:** `200 OK` (empty response body means success)

**Verify upload:**
- Check S3 bucket: `aws s3 ls s3://your-bucket/env/userId/jobId/`
- Or use AWS Console to verify the file exists

#### 4. Update Progress

```bash
curl -X POST http://localhost:8080/commands/upload/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "photoId": "ph_xyz789...",
    "bytesSent": 524288,
    "bytesTotal": 1048576,
    "percent": 50
  }'
```

**Expected:** `202 Accepted`

#### 5. Verify Database State

```sql
-- Check upload job
SELECT * FROM upload_jobs WHERE job_id = 'job_abc123...';

-- Check photos
SELECT photo_id, filename, status, s3_key FROM photos WHERE job_id = 'job_abc123...';

-- Check outbox events
SELECT event_type, aggregate_id, payload FROM outbox_events WHERE processed_at IS NULL;
```

---

## 4. Test Data Examples

### Valid File Requests

```json
{
  "filename": "vacation-photo.jpg",
  "mimeType": "image/jpeg",
  "bytes": 3145728
}
```

```json
{
  "filename": "screenshot.png",
  "mimeType": "image/png",
  "bytes": 1048576
}
```

### Invalid File Requests (Should Fail)

```json
// Invalid extension
{
  "filename": "document.pdf",
  "mimeType": "image/jpeg",
  "bytes": 1024
}

// Invalid MIME type
{
  "filename": "photo.jpg",
  "mimeType": "application/pdf",
  "bytes": 1024
}

// File too large
{
  "filename": "huge.jpg",
  "mimeType": "image/jpeg",
  "bytes": 104857600
}

// Empty filename
{
  "filename": "",
  "mimeType": "image/jpeg",
  "bytes": 1024
}
```

---

## 5. Quick Test Script

For automated end-to-end testing including actual S3 uploads:

```bash
# 1. Create test images
./create-test-images.sh

# 2. Start the application (in another terminal)
mvn spring-boot:run

# 3. Run the test script
./test-phase2.sh
```

The script will:
- Register/login to get JWT token
- Create upload jobs (single-part and multipart)
- Update progress
- Test validation and authentication
- **Upload actual images to S3** (if test images exist)

---

## 6. Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=UploadJobTest
```

### Run Integration Tests Only
```bash
mvn test -Dtest=*IntegrationTest
```

### Run with Coverage
```bash
mvn clean test jacoco:report
```

---

## 7. Test Checklist

### ✓ Command API
- [ ] POST `/commands/upload-jobs` accepts batch of files (1-100)
- [ ] Returns presigned URLs within 500ms (p95) for 10 files
- [ ] Validates filenames, MIME types, and size limits
- [ ] Rejects oversized files (>50MB) and unsupported formats

### ✓ Single-Part Uploads
- [ ] Generates valid presigned PUT URLs with 15-minute expiration
- [ ] Client can upload file directly to S3 using presigned URL
- [ ] S3 key follows pattern: `${env}/${userId}/${jobId}/${photoId}.ext`

### ✓ Multipart Uploads
- [ ] Triggers multipart for files > 5MB when strategy = `S3_MULTIPART`
- [ ] Returns uploadId and presigned URLs for each part
- [ ] Part size defaults to 8MB

### ✓ Domain Model
- [ ] `UploadJob` and `Photo` entities persist correctly
- [ ] Domain events registered on aggregate creation
- [ ] Status transitions follow invariants

### ✓ Transactional Outbox
- [ ] Domain events persisted to `outbox_events` table atomically
- [ ] Events contain correct JSON payload
- [ ] Unprocessed events queryable

### ✓ Progress Tracking
- [ ] POST `/commands/upload/progress` accepts progress updates
- [ ] Updates photo status to UPLOADING when progress > 0%
- [ ] Returns 202 Accepted (best-effort)

### ✓ Security
- [ ] All command endpoints require valid JWT with `photos:write` scope
- [ ] Users can only create jobs/photos for their own userId
- [ ] Presigned URLs scoped to user's S3 prefix

---

## 8. Troubleshooting

### Test Images Not Created
If `create-test-images.sh` fails:
- **macOS**: Install ImageMagick: `brew install imagemagick`
- **Linux**: Install ImageMagick: `sudo apt-get install imagemagick` or `sudo yum install ImageMagick`
- **Alternative**: Install Python PIL: `pip3 install Pillow`
- **Manual**: Create your own test images and place them in `./test-images/`

### S3 Upload Fails
- Verify AWS credentials are configured: `aws configure list`
- Check S3 bucket exists and is accessible
- Verify presigned URL hasn't expired (15 minutes)
- Check bucket CORS configuration allows PUT requests

### Authentication Fails
- Verify application is running: `curl http://localhost:8080/actuator/health`
- Check JWT secret matches between registration and login
- Verify token is included in Authorization header: `Bearer <token>`

---

## Next Steps

After Phase 2 testing is complete:
1. **Phase 3**: Implement async processing pipeline (S3 events → SQS → processor)
2. **Load Testing**: Use k6 or Gatling to test concurrent uploads
3. **Monitoring**: Add metrics and logging for production observability

