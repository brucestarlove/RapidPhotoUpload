# Phase 2: Core Upload Flow (Presigned URLs)

**Status**: Command API Implementation  
**Duration Estimate**: 2-3 weeks  
**Dependencies**: Phase 1 (Foundation)

---

## Overview

Implement the command-side API for creating upload jobs and generating S3 presigned URLs. This phase enables clients (web/mobile) to upload files directly to S3 without proxying through the application server, minimizing server load and maximizing throughput for concurrent uploads.

---

## Goals

1. Implement `POST /commands/upload-jobs` endpoint to create batch upload jobs
2. Generate S3 presigned PUT URLs using AWS SDK Java v2 `S3Presigner`
3. Implement domain aggregates: `UploadJob` and `Photo`
4. Establish Transactional Outbox pattern for reliable event publishing
5. Add best-effort progress tracking endpoint
6. Implement multipart upload support for large files (>5MB)

---

## Technical Stack

### New Dependencies

```xml
<!-- Spring Cloud AWS (for future SQS integration, prepare now) -->
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter</artifactId>
  <version>3.1.0</version>
</dependency>

<!-- Jackson for JSON serialization of events -->
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## Deliverables

### 1. Domain Model: UploadJob & Photo Aggregates

#### UploadJob Aggregate

**`features/uploadphoto/domain/UploadJob.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.AggregateRoot;
import com.starscape.rapidupload.features.uploadphoto.domain.events.UploadJobCreated;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "upload_jobs")
public class UploadJob extends AggregateRoot<String> {
    
    @Id
    @Column(name = "job_id")
    private String jobId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "total_count", nullable = false)
    private int totalCount;
    
    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;
    
    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;
    
    @Column(name = "cancelled_count", nullable = false)
    private int cancelledCount = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadJobStatus status;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @OneToMany(mappedBy = "uploadJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Photo> photos = new ArrayList<>();
    
    protected UploadJob() {
        // JPA constructor
    }
    
    public UploadJob(String jobId, String userId, int totalCount) {
        super(jobId);
        this.jobId = jobId;
        this.userId = userId;
        this.totalCount = totalCount;
        this.status = UploadJobStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        
        // Domain event
        registerEvent(new UploadJobCreated(jobId, userId, totalCount, createdAt));
    }
    
    @Override
    public String getId() {
        return jobId;
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public int getCompletedCount() {
        return completedCount;
    }
    
    public int getFailedCount() {
        return failedCount;
    }
    
    public int getCancelledCount() {
        return cancelledCount;
    }
    
    public UploadJobStatus getStatus() {
        return status;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public List<Photo> getPhotos() {
        return Collections.unmodifiableList(photos);
    }
    
    public void addPhoto(Photo photo) {
        photos.add(photo);
        photo.setUploadJob(this);
    }
    
    public void markInProgress() {
        if (status == UploadJobStatus.QUEUED) {
            this.status = UploadJobStatus.IN_PROGRESS;
            this.updatedAt = Instant.now();
        }
    }
    
    public void updateProgress() {
        long completed = photos.stream().filter(p -> p.getStatus() == PhotoStatus.COMPLETED).count();
        long failed = photos.stream().filter(p -> p.getStatus() == PhotoStatus.FAILED).count();
        long cancelled = photos.stream().filter(p -> p.getStatus() == PhotoStatus.CANCELLED).count();
        
        this.completedCount = (int) completed;
        this.failedCount = (int) failed;
        this.cancelledCount = (int) cancelled;
        this.updatedAt = Instant.now();
        
        // Check if job is complete
        int terminalCount = completedCount + failedCount + cancelledCount;
        if (terminalCount == totalCount) {
            if (failedCount == 0 && cancelledCount == 0) {
                this.status = UploadJobStatus.COMPLETED;
            } else if (completedCount > 0) {
                this.status = UploadJobStatus.COMPLETED_WITH_ERRORS;
            } else {
                this.status = UploadJobStatus.FAILED;
            }
        } else if (status == UploadJobStatus.QUEUED && terminalCount > 0) {
            this.status = UploadJobStatus.IN_PROGRESS;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

**`features/uploadphoto/domain/UploadJobStatus.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

public enum UploadJobStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    COMPLETED_WITH_ERRORS
}
```

**`features/uploadphoto/domain/UploadJobRepository.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import java.util.Optional;

public interface UploadJobRepository {
    UploadJob save(UploadJob job);
    Optional<UploadJob> findById(String jobId);
    Optional<UploadJob> findByIdWithPhotos(String jobId);
}
```

#### Photo Aggregate

**`features/uploadphoto/domain/Photo.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.AggregateRoot;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoQueued;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "photos")
public class Photo extends AggregateRoot<String> {
    
    @Id
    @Column(name = "photo_id")
    private String photoId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private UploadJob uploadJob;
    
    @Column(name = "job_id", insertable = false, updatable = false)
    private String jobId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    
    @Column(nullable = false)
    private long bytes;
    
    @Column(name = "s3_key")
    private String s3Key;
    
    @Column(name = "s3_bucket")
    private String s3Bucket;
    
    @Column(name = "etag")
    private String etag;
    
    @Column(name = "checksum")
    private String checksum;
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @Column(name = "exif_json", columnDefinition = "jsonb")
    private String exifJson;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStatus status;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    protected Photo() {
        // JPA constructor
    }
    
    public Photo(String photoId, String userId, String filename, String mimeType, long bytes) {
        super(photoId);
        validateInput(photoId, userId, filename, mimeType, bytes);
        
        this.photoId = photoId;
        this.userId = userId;
        this.filename = filename;
        this.mimeType = mimeType;
        this.bytes = bytes;
        this.status = PhotoStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        
        // Domain event
        registerEvent(new PhotoQueued(photoId, userId, filename, bytes, createdAt));
    }
    
    private void validateInput(String photoId, String userId, String filename, String mimeType, long bytes) {
        if (photoId == null || photoId.isBlank()) {
            throw new IllegalArgumentException("Photo ID cannot be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be blank");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("MIME type cannot be blank");
        }
        if (bytes <= 0) {
            throw new IllegalArgumentException("Bytes must be positive");
        }
    }
    
    @Override
    public String getId() {
        return photoId;
    }
    
    // Getters
    public String getPhotoId() { return photoId; }
    public String getJobId() { return jobId; }
    public String getUserId() { return userId; }
    public String getFilename() { return filename; }
    public String getMimeType() { return mimeType; }
    public long getBytes() { return bytes; }
    public String getS3Key() { return s3Key; }
    public String getS3Bucket() { return s3Bucket; }
    public String getEtag() { return etag; }
    public String getChecksum() { return checksum; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getExifJson() { return exifJson; }
    public PhotoStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    
    public void setUploadJob(UploadJob uploadJob) {
        this.uploadJob = uploadJob;
        this.jobId = uploadJob.getJobId();
    }
    
    public void markUploading() {
        if (status == PhotoStatus.QUEUED) {
            this.status = PhotoStatus.UPLOADING;
            this.updatedAt = Instant.now();
        }
    }
    
    public void markProcessing(String s3Key, String s3Bucket, String etag) {
        if (status == PhotoStatus.UPLOADING || status == PhotoStatus.QUEUED) {
            this.s3Key = s3Key;
            this.s3Bucket = s3Bucket;
            this.etag = etag;
            this.status = PhotoStatus.PROCESSING;
            this.updatedAt = Instant.now();
        }
    }
    
    public void markCompleted(Integer width, Integer height, String exifJson, String checksum) {
        if (status == PhotoStatus.PROCESSING) {
            this.width = width;
            this.height = height;
            this.exifJson = exifJson;
            this.checksum = checksum;
            this.status = PhotoStatus.COMPLETED;
            this.completedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }
    
    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = PhotoStatus.FAILED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public void cancel() {
        if (status != PhotoStatus.COMPLETED && status != PhotoStatus.FAILED) {
            this.status = PhotoStatus.CANCELLED;
            this.completedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

**`features/uploadphoto/domain/PhotoStatus.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

public enum PhotoStatus {
    QUEUED,
    UPLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

**`features/uploadphoto/domain/PhotoRepository.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository {
    Photo save(Photo photo);
    Optional<Photo> findById(String photoId);
    List<Photo> findByJobId(String jobId);
    Optional<Photo> findByS3Key(String s3Key);
}
```

---

### 2. Domain Events

**`features/uploadphoto/domain/events/UploadJobCreated.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

public record UploadJobCreated(
    String jobId,
    String userId,
    int totalCount,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "UploadJobCreated";
    }
    
    @Override
    public String getAggregateId() {
        return jobId;
    }
    
    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }
}
```

**`features/uploadphoto/domain/events/PhotoQueued.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

public record PhotoQueued(
    String photoId,
    String userId,
    String filename,
    long bytes,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "PhotoQueued";
    }
    
    @Override
    public String getAggregateId() {
        return photoId;
    }
    
    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }
}
```

---

### 3. Transactional Outbox Implementation

**`common/outbox/OutboxEvent.java`**
```java
package com.starscape.rapidupload.common.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    
    @Id
    @Column(name = "event_id")
    private String eventId;
    
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    protected OutboxEvent() {
        // JPA constructor
    }
    
    public OutboxEvent(String eventId, String aggregateType, String aggregateId, 
                       String eventType, String payload) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }
    
    // Getters
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    
    public void markProcessed() {
        this.processedAt = Instant.now();
    }
    
    public boolean isProcessed() {
        return processedAt != null;
    }
}
```

**`common/outbox/OutboxEventRepository.java`**
```java
package com.starscape.rapidupload.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnprocessedEvents();
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NULL ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findUnprocessedEventsWithLimit(int limit);
}
```

**`common/outbox/OutboxService.java`**
```java
package com.starscape.rapidupload.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.domain.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {
    
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    public OutboxService(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public void publish(DomainEvent event, String aggregateType) {
        try {
            String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = new OutboxEvent(
                eventId,
                aggregateType,
                event.getAggregateId(),
                event.getEventType(),
                payload
            );
            
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
```

---

### 4. S3 Presigner Service

**`features/uploadphoto/infra/S3PresignService.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
public class S3PresignService {
    
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final int presignDurationMinutes;
    
    public S3PresignService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.presign-duration-minutes}") int presignDurationMinutes) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignDurationMinutes = presignDurationMinutes;
    }
    
    public PresignedUploadUrl generatePresignedPutUrl(
            String s3Key, 
            String contentType, 
            long contentLength) {
        
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                .putObjectRequest(putRequest)
                .build();
        
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        
        return new PresignedUploadUrl(
            presignedRequest.url().toString(),
            "PUT",
            bucket,
            s3Key,
            presignDurationMinutes * 60
        );
    }
    
    public record PresignedUploadUrl(
        String url,
        String method,
        String bucket,
        String key,
        int expiresInSeconds
    ) {}
}
```

**Note**: Multipart upload support will be added for files > 5MB:

**`features/uploadphoto/infra/S3MultipartPresignService.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class S3MultipartPresignService {
    
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final int presignDurationMinutes;
    
    // Minimum 5MB per part (AWS requirement), optimal 8-16MB
    private static final long DEFAULT_PART_SIZE = 8 * 1024 * 1024; // 8MB
    private static final long MULTIPART_THRESHOLD = 5 * 1024 * 1024; // 5MB
    
    public S3MultipartPresignService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.presign-duration-minutes}") int presignDurationMinutes) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignDurationMinutes = presignDurationMinutes;
    }
    
    public boolean shouldUseMultipart(long fileSize) {
        return fileSize > MULTIPART_THRESHOLD;
    }
    
    public MultipartUploadInfo initiateMultipartUpload(
            String s3Key, 
            String contentType,
            long totalBytes) {
        
        // Create multipart upload
        CreateMultipartUploadRequest multipartRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build();
        
        CreateMultipartUploadPresignRequest presignMultipartRequest = 
            CreateMultipartUploadPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                .createMultipartUploadRequest(multipartRequest)
                .build();
        
        PresignedCreateMultipartUploadRequest presignedMultipart = 
            s3Presigner.presignCreateMultipartUpload(presignMultipartRequest);
        
        // Calculate number of parts
        int partCount = (int) Math.ceil((double) totalBytes / DEFAULT_PART_SIZE);
        
        // Generate presigned URLs for each part
        List<PartUploadUrl> partUrls = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            long partSize = Math.min(DEFAULT_PART_SIZE, totalBytes - (partNumber - 1) * DEFAULT_PART_SIZE);
            
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .uploadId(presignedMultipart.uploadId())
                    .partNumber(partNumber)
                    .contentLength(partSize)
                    .build();
            
            UploadPartPresignRequest presignPartRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                    .uploadPartRequest(uploadPartRequest)
                    .build();
            
            PresignedUploadPartRequest presignedPart = s3Presigner.presignUploadPart(presignPartRequest);
            
            partUrls.add(new PartUploadUrl(
                partNumber,
                presignedPart.url().toString(),
                partSize
            ));
        }
        
        return new MultipartUploadInfo(
            presignedMultipart.uploadId(),
            s3Key,
            bucket,
            DEFAULT_PART_SIZE,
            partUrls,
            presignDurationMinutes * 60
        );
    }
    
    public record MultipartUploadInfo(
        String uploadId,
        String key,
        String bucket,
        long partSize,
        List<PartUploadUrl> parts,
        int expiresInSeconds
    ) {}
    
    public record PartUploadUrl(
        int partNumber,
        String url,
        long size
    ) {}
}
```

---

### 5. Command API Implementation

#### DTOs

**`features/uploadphoto/api/dto/CreateUploadJobRequest.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUploadJobRequest(
    @NotEmpty(message = "Files list cannot be empty")
    @Size(max = 100, message = "Maximum 100 files per batch")
    @Valid
    List<FileUploadRequest> files,
    
    @NotNull(message = "Strategy is required")
    UploadStrategy strategy
) {}
```

**`features/uploadphoto/api/dto/FileUploadRequest.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.constraints.*;

public record FileUploadRequest(
    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename too long")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+\\.(jpg|jpeg|png|gif|webp|heic|heif)$", 
             message = "Invalid filename or extension")
    String filename,
    
    @NotBlank(message = "MIME type is required")
    @Pattern(regexp = "^image/(jpeg|png|gif|webp|heic|heif)$", 
             message = "Unsupported MIME type")
    String mimeType,
    
    @Positive(message = "File size must be positive")
    @Max(value = 52428800, message = "File size exceeds maximum (50MB)")
    long bytes
) {}
```

**`features/uploadphoto/api/dto/UploadStrategy.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

public enum UploadStrategy {
    S3_PRESIGNED,
    S3_MULTIPART
}
```

**`features/uploadphoto/api/dto/CreateUploadJobResponse.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

import java.util.List;

public record CreateUploadJobResponse(
    String jobId,
    List<PhotoUploadItem> items
) {}
```

**`features/uploadphoto/api/dto/PhotoUploadItem.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

public record PhotoUploadItem(
    String photoId,
    String method,
    String presignedUrl,
    MultipartUploadInfo multipart
) {
    public static PhotoUploadItem singlePart(String photoId, String presignedUrl) {
        return new PhotoUploadItem(photoId, "PUT", presignedUrl, null);
    }
    
    public static PhotoUploadItem multipart(String photoId, MultipartUploadInfo multipart) {
        return new PhotoUploadItem(photoId, "MULTIPART", null, multipart);
    }
}
```

**`features/uploadphoto/api/dto/MultipartUploadInfo.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

import java.util.List;

public record MultipartUploadInfo(
    String uploadId,
    long partSize,
    List<PartUrl> parts
) {}
```

**`features/uploadphoto/api/dto/PartUrl.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

public record PartUrl(
    int partNumber,
    String url,
    long size
) {}
```

**`features/uploadphoto/api/dto/UpdateProgressRequest.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProgressRequest(
    @NotBlank(message = "Photo ID is required")
    String photoId,
    
    @PositiveOrZero(message = "Bytes sent cannot be negative")
    long bytesSent,
    
    @PositiveOrZero(message = "Bytes total cannot be negative")
    long bytesTotal,
    
    @Min(value = 0, message = "Percent must be between 0 and 100")
    @Max(value = 100, message = "Percent must be between 0 and 100")
    int percent
) {}
```

#### Controller

**`features/uploadphoto/api/UploadPhotoController.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.uploadphoto.api.dto.*;
import com.starscape.rapidupload.features.uploadphoto.app.CreateUploadJobHandler;
import com.starscape.rapidupload.features.uploadphoto.app.UpdateProgressHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/commands")
public class UploadPhotoController {
    
    private final CreateUploadJobHandler createUploadJobHandler;
    private final UpdateProgressHandler updateProgressHandler;
    
    public UploadPhotoController(
            CreateUploadJobHandler createUploadJobHandler,
            UpdateProgressHandler updateProgressHandler) {
        this.createUploadJobHandler = createUploadJobHandler;
        this.updateProgressHandler = updateProgressHandler;
    }
    
    @PostMapping("/upload-jobs")
    public ResponseEntity<CreateUploadJobResponse> createUploadJob(
            @Valid @RequestBody CreateUploadJobRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CreateUploadJobResponse response = createUploadJobHandler.handle(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/upload/progress")
    public ResponseEntity<Void> updateProgress(
            @Valid @RequestBody UpdateProgressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        updateProgressHandler.handle(request, principal.getUserId());
        return ResponseEntity.accepted().build();
    }
}
```

---

### 6. Application Handlers

**`features/uploadphoto/app/CreateUploadJobHandler.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.app;

import com.starscape.rapidupload.common.outbox.OutboxService;
import com.starscape.rapidupload.features.uploadphoto.api.dto.*;
import com.starscape.rapidupload.features.uploadphoto.domain.*;
import com.starscape.rapidupload.features.uploadphoto.infra.S3MultipartPresignService;
import com.starscape.rapidupload.features.uploadphoto.infra.S3PresignService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CreateUploadJobHandler {
    
    private final UploadJobRepository uploadJobRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignService s3PresignService;
    private final S3MultipartPresignService s3MultipartPresignService;
    private final OutboxService outboxService;
    private final String environment;
    
    public CreateUploadJobHandler(
            UploadJobRepository uploadJobRepository,
            PhotoRepository photoRepository,
            S3PresignService s3PresignService,
            S3MultipartPresignService s3MultipartPresignService,
            OutboxService outboxService,
            @Value("${spring.profiles.active:dev}") String environment) {
        this.uploadJobRepository = uploadJobRepository;
        this.photoRepository = photoRepository;
        this.s3PresignService = s3PresignService;
        this.s3MultipartPresignService = s3MultipartPresignService;
        this.outboxService = outboxService;
        this.environment = environment;
    }
    
    @Transactional
    public CreateUploadJobResponse handle(CreateUploadJobRequest request, String userId) {
        // Create job
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "");
        UploadJob job = new UploadJob(jobId, userId, request.files().size());
        
        List<PhotoUploadItem> items = new ArrayList<>();
        
        // Create photos and generate presigned URLs
        for (FileUploadRequest fileRequest : request.files()) {
            String photoId = "ph_" + UUID.randomUUID().toString().replace("-", "");
            Photo photo = new Photo(
                photoId,
                userId,
                fileRequest.filename(),
                fileRequest.mimeType(),
                fileRequest.bytes()
            );
            
            job.addPhoto(photo);
            
            // Generate S3 key: env/userId/jobId/photoId.ext
            String extension = extractExtension(fileRequest.filename());
            String s3Key = String.format("%s/%s/%s/%s%s", 
                environment, userId, jobId, photoId, extension);
            
            // Determine upload strategy
            PhotoUploadItem item;
            if (s3MultipartPresignService.shouldUseMultipart(fileRequest.bytes()) && 
                request.strategy() == UploadStrategy.S3_MULTIPART) {
                
                // Multipart upload
                var multipartInfo = s3MultipartPresignService.initiateMultipartUpload(
                    s3Key,
                    fileRequest.mimeType(),
                    fileRequest.bytes()
                );
                
                List<PartUrl> partUrls = multipartInfo.parts().stream()
                    .map(p -> new PartUrl(p.partNumber(), p.url(), p.size()))
                    .toList();
                
                item = PhotoUploadItem.multipart(
                    photoId,
                    new MultipartUploadInfo(
                        multipartInfo.uploadId(),
                        multipartInfo.partSize(),
                        partUrls
                    )
                );
            } else {
                // Single-part upload
                var presignedUrl = s3PresignService.generatePresignedPutUrl(
                    s3Key,
                    fileRequest.mimeType(),
                    fileRequest.bytes()
                );
                
                item = PhotoUploadItem.singlePart(photoId, presignedUrl.url());
            }
            
            items.add(item);
            
            // Publish photo event
            photo.getDomainEvents().forEach(event -> 
                outboxService.publish(event, "Photo"));
        }
        
        // Save job (cascades to photos)
        uploadJobRepository.save(job);
        
        // Publish job events
        job.getDomainEvents().forEach(event -> 
            outboxService.publish(event, "UploadJob"));
        
        return new CreateUploadJobResponse(jobId, items);
    }
    
    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }
}
```

**`features/uploadphoto/app/UpdateProgressHandler.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.uploadphoto.api.dto.UpdateProgressRequest;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateProgressHandler {
    
    private static final Logger log = LoggerFactory.getLogger(UpdateProgressHandler.class);
    
    private final PhotoRepository photoRepository;
    
    public UpdateProgressHandler(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }
    
    @Transactional
    public void handle(UpdateProgressRequest request, String userId) {
        Photo photo = photoRepository.findById(request.photoId())
                .orElseThrow(() -> new NotFoundException("Photo not found: " + request.photoId()));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Best-effort: mark as uploading if progress is being reported
        if (request.percent() > 0 && request.percent() < 100) {
            photo.markUploading();
            photoRepository.save(photo);
            log.debug("Photo {} progress: {}%", request.photoId(), request.percent());
        }
    }
}
```

---

### 7. Infrastructure Repositories

**`features/uploadphoto/infra/JpaUploadJobRepository.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaUploadJobRepository extends JpaRepository<UploadJob, String>, UploadJobRepository {
    
    @Query("SELECT j FROM UploadJob j LEFT JOIN FETCH j.photos WHERE j.jobId = :jobId")
    Optional<UploadJob> findByIdWithPhotos(@Param("jobId") String jobId);
}
```

**`features/uploadphoto/infra/JpaPhotoRepository.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaPhotoRepository extends JpaRepository<Photo, String>, PhotoRepository {
    
    List<Photo> findByJobId(String jobId);
    
    Optional<Photo> findByS3Key(String s3Key);
}
```

---

## Acceptance Criteria

### ✓ Command API
- [ ] POST `/commands/upload-jobs` accepts batch of files (1-100)
- [ ] Returns presigned URLs for each file within 500ms (p95) for 10 files
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
- [ ] Status transitions follow invariants (QUEUED → UPLOADING → PROCESSING)

### ✓ Transactional Outbox
- [ ] Domain events persisted to `outbox_events` table atomically with aggregates
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

## Next Steps

Upon completion of Phase 2:
1. **Test** presigned URL generation and direct S3 upload
2. **Verify** outbox events are created
3. **Proceed** to Phase 3: Async Processing Pipeline (S3 events → SQS → processor)

---

## References

- **AWS SDK Java v2 S3Presigner**: https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html
- **S3 Multipart Upload**: https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html
- **Transactional Outbox Pattern**: https://microservices.io/patterns/data/transactional-outbox.html

---

**Phase 2 Complete** → Ready for Phase 3 (Async Processing Pipeline)

