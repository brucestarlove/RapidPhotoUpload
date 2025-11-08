# Phase 4: Real-time Progress &amp; Query APIs

**Status**: WebSocket + CQRS Read Side  
**Duration Estimate**: 2-3 weeks  
**Dependencies**: Phase 3 (Async Processing Pipeline)

---

## Overview

Implement the read side of the CQRS architecture: real-time WebSocket/SSE push notifications for upload progress and query APIs for retrieving photo metadata, job status, and generating download URLs. This phase completes the user experience loop, providing live feedback as uploads are processed.

---

## Goals

1. Implement WebSocket/STOMP endpoint for real-time progress updates
2. Build query APIs for jobs, photos, and filtered lists
3. Generate presigned GET URLs for secure downloads
4. Create read-optimized projections and DTOs
5. Implement event listeners that broadcast to WebSocket clients
6. Add pagination, filtering, and search capabilities

---

## Technical Stack

### New Dependencies

```xml
<!-- WebSocket support -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- STOMP messaging -->
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-messaging</artifactId>
</dependency>

<!-- SockJS (WebSocket fallback) -->
<dependency>
  <groupId>org.webjars</groupId>
  <artifactId>sockjs-client</artifactId>
  <version>1.5.1</version>
</dependency>

<dependency>
  <groupId>org.webjars</groupId>
  <artifactId>stomp-websocket</artifactId>
  <version>2.3.4</version>
</dependency>
```

---

## Deliverables

### 1. WebSocket Configuration

**`common/config/WebSocketConfig.java`**
```java
package com.starscape.rapidupload.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple broker for topic and queue destinations
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Prefix for client messages
        registry.setApplicationDestinationPrefixes("/app");
        
        // Prefix for user-specific messages
        registry.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Configure properly in production
                .withSockJS();
    }
}
```

---

### 2. Progress Tracking Feature (WebSocket)

#### Progress Event DTOs

**`features/trackprogress/api/dto/ProgressUpdate.java`**
```java
package com.starscape.rapidupload.features.trackprogress.api.dto;

import java.time.Instant;

public record ProgressUpdate(
    String jobId,
    String photoId,
    String status,
    int progressPercent,
    String message,
    Instant timestamp
) {
    public static ProgressUpdate of(String jobId, String photoId, String status, int percent, String message) {
        return new ProgressUpdate(jobId, photoId, status, percent, message, Instant.now());
    }
}
```

**`features/trackprogress/api/dto/JobStatusUpdate.java`**
```java
package com.starscape.rapidupload.features.trackprogress.api.dto;

import java.time.Instant;

public record JobStatusUpdate(
    String jobId,
    String status,
    int totalCount,
    int completedCount,
    int failedCount,
    int cancelledCount,
    Instant timestamp
) {
    public static JobStatusUpdate of(
            String jobId, 
            String status, 
            int total, 
            int completed, 
            int failed, 
            int cancelled) {
        return new JobStatusUpdate(jobId, status, total, completed, failed, cancelled, Instant.now());
    }
}
```

#### WebSocket Event Broadcaster

**`features/trackprogress/app/ProgressBroadcaster.java`**
```java
package com.starscape.rapidupload.features.trackprogress.app;

import com.starscape.rapidupload.features.trackprogress.api.dto.JobStatusUpdate;
import com.starscape.rapidupload.features.trackprogress.api.dto.ProgressUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProgressBroadcaster {
    
    private static final Logger log = LoggerFactory.getLogger(ProgressBroadcaster.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public ProgressBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Broadcast progress update to all subscribers of a job topic
     */
    public void broadcastProgress(ProgressUpdate update) {
        String destination = "/topic/job/" + update.jobId();
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Broadcasted progress to {}: photoId={}, status={}", 
            destination, update.photoId(), update.status());
    }
    
    /**
     * Send progress update to a specific user
     */
    public void sendToUser(String userId, ProgressUpdate update) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/progress", update);
        log.debug("Sent progress to user {}: photoId={}", userId, update.photoId());
    }
    
    /**
     * Broadcast job status update
     */
    public void broadcastJobStatus(JobStatusUpdate update) {
        String destination = "/topic/job/" + update.jobId();
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Broadcasted job status to {}: status={}, completed={}/{}", 
            destination, update.status(), update.completedCount(), update.totalCount());
    }
}
```

#### Domain Event Listeners

**`features/trackprogress/app/PhotoEventListener.java`**
```java
package com.starscape.rapidupload.features.trackprogress.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.outbox.OutboxEvent;
import com.starscape.rapidupload.common.outbox.OutboxEventRepository;
import com.starscape.rapidupload.features.trackprogress.api.dto.ProgressUpdate;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoProcessingCompleted;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoFailed;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoQueued;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PhotoEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(PhotoEventListener.class);
    
    private final OutboxEventRepository outboxRepository;
    private final ProgressBroadcaster progressBroadcaster;
    private final ObjectMapper objectMapper;
    
    public PhotoEventListener(
            OutboxEventRepository outboxRepository,
            ProgressBroadcaster progressBroadcaster,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.progressBroadcaster = progressBroadcaster;
        this.objectMapper = objectMapper;
    }
    
    @Scheduled(fixedDelay = 2000)  // Every 2 seconds
    @Transactional
    public void processPhotoEvents() {
        List<OutboxEvent> events = outboxRepository.findUnprocessedEventsWithLimit(50);
        
        if (events.isEmpty()) {
            return;
        }
        
        for (OutboxEvent event : events) {
            try {
                handleEvent(event);
                // Events already marked processed by JobProgressAggregator
            } catch (Exception e) {
                log.error("Failed to broadcast event: {}", event.getEventId(), e);
            }
        }
    }
    
    private void handleEvent(OutboxEvent event) throws JsonProcessingException {
        switch (event.getEventType()) {
            case "PhotoQueued" -> {
                PhotoQueued photoEvent = objectMapper.readValue(event.getPayload(), PhotoQueued.class);
                ProgressUpdate update = ProgressUpdate.of(
                    extractJobId(photoEvent.photoId()),
                    photoEvent.photoId(),
                    "QUEUED",
                    0,
                    "Photo queued for upload"
                );
                progressBroadcaster.broadcastProgress(update);
            }
            case "PhotoProcessingCompleted" -> {
                PhotoProcessingCompleted photoEvent = objectMapper.readValue(
                    event.getPayload(), PhotoProcessingCompleted.class);
                ProgressUpdate update = ProgressUpdate.of(
                    photoEvent.jobId(),
                    photoEvent.photoId(),
                    "COMPLETED",
                    100,
                    "Processing complete"
                );
                progressBroadcaster.broadcastProgress(update);
            }
            case "PhotoFailed" -> {
                PhotoFailed photoEvent = objectMapper.readValue(event.getPayload(), PhotoFailed.class);
                ProgressUpdate update = ProgressUpdate.of(
                    photoEvent.jobId(),
                    photoEvent.photoId(),
                    "FAILED",
                    0,
                    photoEvent.errorMessage()
                );
                progressBroadcaster.broadcastProgress(update);
            }
        }
    }
    
    private String extractJobId(String photoId) {
        // Fallback if jobId not in event (shouldn't happen in practice)
        return "unknown";
    }
}
```

**`features/trackprogress/app/JobEventListener.java`**
```java
package com.starscape.rapidupload.features.trackprogress.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.outbox.OutboxEvent;
import com.starscape.rapidupload.common.outbox.OutboxEventRepository;
import com.starscape.rapidupload.features.trackprogress.api.dto.JobStatusUpdate;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class JobEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(JobEventListener.class);
    
    private final OutboxEventRepository outboxRepository;
    private final UploadJobRepository jobRepository;
    private final ProgressBroadcaster progressBroadcaster;
    private final ObjectMapper objectMapper;
    
    // Track jobs we've recently updated to avoid excessive broadcasts
    private final Set<String> recentlyUpdated = new HashSet<>();
    
    public JobEventListener(
            OutboxEventRepository outboxRepository,
            UploadJobRepository jobRepository,
            ProgressBroadcaster progressBroadcaster,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.jobRepository = jobRepository;
        this.progressBroadcaster = progressBroadcaster;
        this.objectMapper = objectMapper;
    }
    
    @Scheduled(fixedDelay = 3000)  // Every 3 seconds
    @Transactional(readOnly = true)
    public void broadcastJobUpdates() {
        // Find jobs that have recent photo events
        List<OutboxEvent> photoEvents = outboxRepository.findUnprocessedEventsWithLimit(100);
        
        Set<String> jobIds = new HashSet<>();
        for (OutboxEvent event : photoEvents) {
            if (event.getAggregateType().equals("Photo")) {
                try {
                    String payload = event.getPayload();
                    // Extract jobId from payload (all photo events have it)
                    if (payload.contains("\"jobId\"")) {
                        var node = objectMapper.readTree(payload);
                        String jobId = node.get("jobId").asText();
                        jobIds.add(jobId);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse event payload", e);
                }
            }
        }
        
        // Broadcast updates for affected jobs
        for (String jobId : jobIds) {
            if (!recentlyUpdated.contains(jobId)) {
                jobRepository.findById(jobId).ifPresent(this::broadcastJobStatus);
                recentlyUpdated.add(jobId);
            }
        }
        
        // Clear cache periodically
        if (recentlyUpdated.size() > 1000) {
            recentlyUpdated.clear();
        }
    }
    
    private void broadcastJobStatus(UploadJob job) {
        JobStatusUpdate update = JobStatusUpdate.of(
            job.getJobId(),
            job.getStatus().name(),
            job.getTotalCount(),
            job.getCompletedCount(),
            job.getFailedCount(),
            job.getCancelledCount()
        );
        progressBroadcaster.broadcastJobStatus(update);
    }
}
```

---

### 3. Query APIs (Read Side)

#### Query DTOs

**`features/getphotometadata/api/dto/PhotoMetadataResponse.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.api.dto;

import java.time.Instant;
import java.util.List;

public record PhotoMetadataResponse(
    String photoId,
    String jobId,
    String filename,
    String mimeType,
    long bytes,
    String status,
    Integer width,
    Integer height,
    String checksum,
    Object exif,
    List<String> thumbnailUrls,
    Instant createdAt,
    Instant completedAt
) {}
```

**`features/listphotos/api/dto/PhotoListItem.java`**
```java
package com.starscape.rapidupload.features.listphotos.api.dto;

import java.time.Instant;

public record PhotoListItem(
    String photoId,
    String filename,
    String mimeType,
    long bytes,
    String status,
    Integer width,
    Integer height,
    String thumbnailUrl,
    Instant createdAt
) {}
```

**`features/listphotos/api/dto/PhotoListResponse.java`**
```java
package com.starscape.rapidupload.features.listphotos.api.dto;

import java.util.List;

public record PhotoListResponse(
    List<PhotoListItem> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
```

**`features/getphotometadata/api/dto/JobStatusResponse.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.api.dto;

import java.time.Instant;
import java.util.List;

public record JobStatusResponse(
    String jobId,
    String status,
    int totalCount,
    int completedCount,
    int failedCount,
    int cancelledCount,
    List<PhotoStatusItem> photos,
    Instant createdAt,
    Instant updatedAt
) {}
```

**`features/getphotometadata/api/dto/PhotoStatusItem.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.api.dto;

public record PhotoStatusItem(
    String photoId,
    String filename,
    String status,
    String errorMessage
) {}
```

#### Query Controllers

**`features/getphotometadata/api/PhotoMetadataController.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.getphotometadata.api.dto.JobStatusResponse;
import com.starscape.rapidupload.features.getphotometadata.api.dto.PhotoMetadataResponse;
import com.starscape.rapidupload.features.getphotometadata.app.GetJobStatusHandler;
import com.starscape.rapidupload.features.getphotometadata.app.GetPhotoMetadataHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queries")
public class PhotoMetadataController {
    
    private final GetPhotoMetadataHandler getPhotoMetadataHandler;
    private final GetJobStatusHandler getJobStatusHandler;
    
    public PhotoMetadataController(
            GetPhotoMetadataHandler getPhotoMetadataHandler,
            GetJobStatusHandler getJobStatusHandler) {
        this.getPhotoMetadataHandler = getPhotoMetadataHandler;
        this.getJobStatusHandler = getJobStatusHandler;
    }
    
    @GetMapping("/photos/{photoId}")
    public ResponseEntity<PhotoMetadataResponse> getPhotoMetadata(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PhotoMetadataResponse response = getPhotoMetadataHandler.handle(photoId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/upload-jobs/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        JobStatusResponse response = getJobStatusHandler.handle(jobId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
```

**`features/listphotos/api/PhotoListController.java`**
```java
package com.starscape.rapidupload.features.listphotos.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import com.starscape.rapidupload.features.listphotos.app.ListPhotosHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queries/photos")
public class PhotoListController {
    
    private final ListPhotosHandler listPhotosHandler;
    
    public PhotoListController(ListPhotosHandler listPhotosHandler) {
        this.listPhotosHandler = listPhotosHandler;
    }
    
    @GetMapping
    public ResponseEntity<PhotoListResponse> listPhotos(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PhotoListResponse response = listPhotosHandler.handle(
            principal.getUserId(), tag, status, q, page, size);
        return ResponseEntity.ok(response);
    }
}
```

#### Query Handlers

**`features/getphotometadata/app/GetPhotoMetadataHandler.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.getphotometadata.api.dto.PhotoMetadataResponse;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.infra.S3PresignService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GetPhotoMetadataHandler {
    
    private final PhotoRepository photoRepository;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;
    private final String bucket;
    
    public GetPhotoMetadataHandler(
            PhotoRepository photoRepository,
            S3Presigner s3Presigner,
            ObjectMapper objectMapper,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoRepository = photoRepository;
        this.s3Presigner = s3Presigner;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
    }
    
    @Transactional(readOnly = true)
    public PhotoMetadataResponse handle(String photoId, String userId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        // Parse EXIF JSON
        Object exif = null;
        if (photo.getExifJson() != null) {
            try {
                exif = objectMapper.readValue(photo.getExifJson(), Object.class);
            } catch (JsonProcessingException e) {
                exif = photo.getExifJson();
            }
        }
        
        // Generate thumbnail URLs (presigned)
        List<String> thumbnailUrls = new ArrayList<>();
        if (photo.getS3Key() != null) {
            List<Integer> sizes = List.of(256, 1024);
            for (int size : sizes) {
                String thumbnailKey = getThumbnailKey(photo.getS3Key(), size);
                String url = generatePresignedGetUrl(thumbnailKey);
                thumbnailUrls.add(url);
            }
        }
        
        return new PhotoMetadataResponse(
            photo.getPhotoId(),
            photo.getJobId(),
            photo.getFilename(),
            photo.getMimeType(),
            photo.getBytes(),
            photo.getStatus().name(),
            photo.getWidth(),
            photo.getHeight(),
            photo.getChecksum(),
            exif,
            thumbnailUrls,
            photo.getCreatedAt(),
            photo.getCompletedAt()
        );
    }
    
    private String getThumbnailKey(String originalKey, int size) {
        int lastSlash = originalKey.lastIndexOf('/');
        String basePath = originalKey.substring(0, lastSlash);
        String filename = originalKey.substring(lastSlash + 1);
        
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot) : "";
        
        return basePath + "/thumbnails/" + name + "_" + size + ext;
    }
    
    private String generatePresignedGetUrl(String s3Key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
```

**`features/getphotometadata/app/GetJobStatusHandler.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.getphotometadata.api.dto.JobStatusResponse;
import com.starscape.rapidupload.features.getphotometadata.api.dto.PhotoStatusItem;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetJobStatusHandler {
    
    private final UploadJobRepository jobRepository;
    
    public GetJobStatusHandler(UploadJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }
    
    @Transactional(readOnly = true)
    public JobStatusResponse handle(String jobId, String userId) {
        UploadJob job = jobRepository.findByIdWithPhotos(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        
        // Verify ownership
        if (!job.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Job does not belong to user");
        }
        
        List<PhotoStatusItem> photoItems = job.getPhotos().stream()
                .map(p -> new PhotoStatusItem(
                    p.getPhotoId(),
                    p.getFilename(),
                    p.getStatus().name(),
                    p.getErrorMessage()
                ))
                .toList();
        
        return new JobStatusResponse(
            job.getJobId(),
            job.getStatus().name(),
            job.getTotalCount(),
            job.getCompletedCount(),
            job.getFailedCount(),
            job.getCancelledCount(),
            photoItems,
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }
}
```

**`features/listphotos/app/ListPhotosHandler.java`**
```java
package com.starscape.rapidupload.features.listphotos.app;

import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListItem;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import com.starscape.rapidupload.features.listphotos.infra.PhotoQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListPhotosHandler {
    
    private final PhotoQueryRepository photoQueryRepository;
    
    public ListPhotosHandler(PhotoQueryRepository photoQueryRepository) {
        this.photoQueryRepository = photoQueryRepository;
    }
    
    @Transactional(readOnly = true)
    public PhotoListResponse handle(
            String userId, 
            String tag, 
            String status, 
            String query, 
            int page, 
            int size) {
        
        // Limit page size
        size = Math.min(size, 100);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Photo> photoPage;
        
        if (status != null && !status.isBlank()) {
            PhotoStatus photoStatus = PhotoStatus.valueOf(status.toUpperCase());
            photoPage = photoQueryRepository.findByUserIdAndStatus(userId, photoStatus, pageable);
        } else if (query != null && !query.isBlank()) {
            photoPage = photoQueryRepository.findByUserIdAndFilenameContaining(userId, query, pageable);
        } else {
            photoPage = photoQueryRepository.findByUserId(userId, pageable);
        }
        
        List<PhotoListItem> items = photoPage.getContent().stream()
                .map(this::toListItem)
                .toList();
        
        return new PhotoListResponse(
            items,
            photoPage.getNumber(),
            photoPage.getSize(),
            photoPage.getTotalElements(),
            photoPage.getTotalPages()
        );
    }
    
    private PhotoListItem toListItem(Photo photo) {
        String thumbnailUrl = null;
        if (photo.getS3Key() != null) {
            // Return placeholder; client can fetch presigned URL separately
            thumbnailUrl = "/queries/photos/" + photo.getPhotoId() + "/thumbnail?size=256";
        }
        
        return new PhotoListItem(
            photo.getPhotoId(),
            photo.getFilename(),
            photo.getMimeType(),
            photo.getBytes(),
            photo.getStatus().name(),
            photo.getWidth(),
            photo.getHeight(),
            thumbnailUrl,
            photo.getCreatedAt()
        );
    }
}
```

---

### 4. Download URL Endpoint

**`features/getphotometadata/api/DownloadController.java`**
```java
package com.starscape.rapidupload.features.getphotometadata.api;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/queries/photos")
public class DownloadController {
    
    private final PhotoRepository photoRepository;
    private final S3Presigner s3Presigner;
    private final String bucket;
    
    public DownloadController(
            PhotoRepository photoRepository,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoRepository = photoRepository;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }
    
    @GetMapping("/{photoId}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        if (photo.getS3Key() == null) {
            throw new IllegalStateException("Photo not yet uploaded");
        }
        
        String presignedUrl = generatePresignedGetUrl(photo.getS3Key(), photo.getFilename());
        
        return ResponseEntity.ok(Map.of(
            "url", presignedUrl,
            "expiresIn", "300"  // 5 minutes
        ));
    }
    
    @GetMapping("/{photoId}/thumbnail")
    public ResponseEntity<Map<String, String>> getThumbnailUrl(
            @PathVariable String photoId,
            @RequestParam(defaultValue = "256") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        if (!photo.getUserId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        if (photo.getS3Key() == null) {
            throw new IllegalStateException("Photo not yet uploaded");
        }
        
        String thumbnailKey = getThumbnailKey(photo.getS3Key(), size);
        String presignedUrl = generatePresignedGetUrl(thumbnailKey, "thumbnail_" + photo.getFilename());
        
        return ResponseEntity.ok(Map.of(
            "url", presignedUrl,
            "expiresIn", "300"
        ));
    }
    
    private String getThumbnailKey(String originalKey, int size) {
        int lastSlash = originalKey.lastIndexOf('/');
        String basePath = originalKey.substring(0, lastSlash);
        String filename = originalKey.substring(lastSlash + 1);
        
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot) : "";
        
        return basePath + "/thumbnails/" + name + "_" + size + ext;
    }
    
    private String generatePresignedGetUrl(String s3Key, String filename) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .responseContentDisposition("attachment; filename=&quot;" + filename + "&quot;")
                .build();
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
```

---

### 5. Query Repository (Read-Optimized)

**`features/listphotos/infra/PhotoQueryRepository.java`**
```java
package com.starscape.rapidupload.features.listphotos.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PhotoQueryRepository extends JpaRepository<Photo, String> {
    
    Page<Photo> findByUserId(String userId, Pageable pageable);
    
    Page<Photo> findByUserIdAndStatus(String userId, PhotoStatus status, Pageable pageable);
    
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND LOWER(p.filename) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Photo> findByUserIdAndFilenameContaining(
        @Param("userId") String userId, 
        @Param("query") String query, 
        Pageable pageable);
}
```

---

### 6. Client-Side WebSocket Example (HTML/JavaScript)

**`static/websocket-example.html`**
```html
<!DOCTYPE html>
<html>
<head>
    <title>RapidPhotoUpload - Live Progress</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1.5.1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <h1>Upload Progress</h1>
    <div id="status">Connecting...</div>
    <div id="progress"></div>
    
    <script>
        const jobId = 'job_abc123'; // Replace with actual job ID
        
        // Connect to WebSocket
        const socket = new SockJS('/ws');
        const stompClient = Stomp.over(socket);
        
        stompClient.connect({}, function(frame) {
            console.log('Connected: ' + frame);
            document.getElementById('status').textContent = 'Connected';
            
            // Subscribe to job-specific topic
            stompClient.subscribe('/topic/job/' + jobId, function(message) {
                const update = JSON.parse(message.body);
                console.log('Progress update:', update);
                displayProgress(update);
            });
        });
        
        function displayProgress(update) {
            const progressDiv = document.getElementById('progress');
            const entry = document.createElement('div');
            
            if (update.photoId) {
                entry.textContent = `Photo ${update.photoId}: ${update.status} (${update.progressPercent}%)`;
            } else {
                entry.textContent = `Job ${update.jobId}: ${update.status} - ${update.completedCount}/${update.totalCount} completed`;
            }
            
            progressDiv.appendChild(entry);
        }
    </script>
</body>
</html>
```

---

## Acceptance Criteria

### ✓ WebSocket Real-time Updates
- [ ] Client connects to `/ws` endpoint via SockJS
- [ ] Client subscribes to `/topic/job/{jobId}` successfully
- [ ] Server broadcasts progress updates when photos transition states
- [ ] Updates received within 1 second of server-side state change
- [ ] WebSocket connection remains stable for long-duration uploads

### ✓ Query APIs
- [ ] GET `/queries/photos/{photoId}` returns full metadata including EXIF
- [ ] GET `/queries/upload-jobs/{jobId}` returns job with all photo statuses
- [ ] GET `/queries/photos` returns paginated list with filters
- [ ] Queries execute in <200ms (p95) for standard datasets

### ✓ Download URLs
- [ ] GET `/queries/photos/{photoId}/download-url` returns valid presigned GET URL
- [ ] Downloaded file matches original (verified by checksum)
- [ ] Thumbnail URLs return correct sized images
- [ ] Presigned URLs expire after 5 minutes

### ✓ Pagination & Filtering
- [ ] Supports pagination with `page` and `size` parameters
- [ ] Filters by status (QUEUED, COMPLETED, FAILED)
- [ ] Filters by filename substring (case-insensitive)
- [ ] Returns total count and page metadata

### ✓ Security
- [ ] All query endpoints require `photos:read` scope
- [ ] Users can only access their own photos and jobs
- [ ] Presigned URLs are scoped to user's S3 prefix

---

## Next Steps

Upon completion of Phase 4:
1. **Test** end-to-end: upload → progress updates via WebSocket → query API
2. **Verify** real-time updates work for concurrent uploads
3. **Benchmark** query performance under load
4. **Proceed** to Phase 5: Observability &amp; Production Readiness

---

## References

- **Spring WebSocket Documentation**: https://spring.io/guides/gs/messaging-stomp-websocket/
- **SockJS Protocol**: https://github.com/sockjs/sockjs-protocol
- **STOMP Protocol**: https://stomp.github.io/
- **AWS S3 Presigned URLs**: https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html

---

**Phase 4 Complete** → Ready for Phase 5 (Observability &amp; Production Readiness)

