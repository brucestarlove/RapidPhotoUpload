# Phase 4: Query APIs &amp; Progress Polling

**Status**: Polling-Based Progress Tracking + CQRS Read Side  
**Duration Estimate**: 2-3 weeks  
**Dependencies**: Phase 3 (Async Processing Pipeline)

---

## Overview

Implement the read side of the CQRS architecture: query APIs for retrieving photo metadata, job status, and generating download URLs. Clients poll the Query API to get progress updates, providing a simple and reliable way to track upload progress without the complexity of WebSocket connections. This phase completes the user experience loop, providing live feedback as uploads are processed.

---

## Goals

1. Build query APIs for jobs, photos, and filtered lists
2. Generate presigned GET URLs for secure downloads
3. Create read-optimized projections and DTOs
4. Document polling-based progress tracking approach
5. Add pagination, filtering, and search capabilities
6. Provide client-side polling implementation guidance

---

## Technical Stack

### No New Dependencies Required

This phase uses existing Spring Boot dependencies. No WebSocket or messaging dependencies are needed.

---

## Deliverables

### 1. Progress Tracking via Polling

Clients poll the `GET /queries/upload-jobs/{jobId}` endpoint to get job status updates. The endpoint returns complete job information including all photo statuses, making it ideal for progress tracking.

**Recommended Polling Implementation:**

```javascript
// Recommended polling implementation
async function pollJobStatus(jobId, authToken) {
  const pollInterval = 1500; // 1.5 seconds
  
  const interval = setInterval(async () => {
    try {
      const response = await fetch(`/queries/upload-jobs/${jobId}`, {
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      
      const jobStatus = await response.json();
      updateUI(jobStatus);
      
      // Stop polling when job is complete
      if (jobStatus.status === 'COMPLETED' || 
          jobStatus.status === 'FAILED' || 
          jobStatus.status === 'CANCELLED') {
        clearInterval(interval);
      }
    } catch (error) {
      console.error('Polling error:', error);
      // Consider exponential backoff on errors
      // For now, continue polling
    }
  }, pollInterval);
  
  return interval; // Return so caller can cancel if needed
}

// Usage example
const jobId = 'job_abc123';
const token = 'your-jwt-token';
const pollInterval = pollJobStatus(jobId, token);

// To cancel polling manually:
// clearInterval(pollInterval);
```

**Polling Best Practices:**

- **Interval**: 1500ms (1.5 seconds) is recommended for active uploads
- **Stop Condition**: Stop polling when job status is `COMPLETED`, `FAILED`, or `CANCELLED`
- **Error Handling**: Implement exponential backoff on consecutive errors
- **Performance**: The endpoint is optimized for frequent polling with read-only transactions
- **Rate Limiting**: Consider implementing client-side rate limiting to avoid excessive requests

---

### 2. Query APIs (Read Side)

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

### 6. Client-Side Polling Example (HTML/JavaScript)

**`static/polling-example.html`**
```html
<!DOCTYPE html>
<html>
<head>
    <title>RapidPhotoUpload - Progress Tracking</title>
</head>
<body>
    <h1>Upload Progress</h1>
    <div id="status">Polling...</div>
    <div id="progress"></div>
    
    <script>
        const jobId = 'job_abc123'; // Replace with actual job ID
        const authToken = 'your-jwt-token'; // Replace with actual token
        
        let pollInterval = null;
        
        function startPolling() {
            pollInterval = setInterval(async () => {
                try {
                    const response = await fetch(`/queries/upload-jobs/${jobId}`, {
                        headers: { 'Authorization': `Bearer ${authToken}` }
                    });
                    
                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}`);
                    }
                    
                    const jobStatus = await response.json();
                    displayProgress(jobStatus);
                    
                    // Stop polling when job is complete
                    if (jobStatus.status === 'COMPLETED' || 
                        jobStatus.status === 'FAILED' || 
                        jobStatus.status === 'CANCELLED') {
                        stopPolling();
                        document.getElementById('status').textContent = 'Complete';
                    }
                } catch (error) {
                    console.error('Polling error:', error);
                    document.getElementById('status').textContent = 'Error: ' + error.message;
                }
            }, 1500); // Poll every 1.5 seconds
        }
        
        function stopPolling() {
            if (pollInterval) {
                clearInterval(pollInterval);
                pollInterval = null;
            }
        }
        
        function displayProgress(jobStatus) {
            const progressDiv = document.getElementById('progress');
            progressDiv.innerHTML = `
                <h2>Job: ${jobStatus.jobId}</h2>
                <p>Status: ${jobStatus.status}</p>
                <p>Progress: ${jobStatus.completedCount}/${jobStatus.totalCount} completed</p>
                <p>Failed: ${jobStatus.failedCount}</p>
                <h3>Photos:</h3>
                <ul>
                    ${jobStatus.photos.map(p => 
                        `<li>${p.filename}: ${p.status}${p.errorMessage ? ' - ' + p.errorMessage : ''}</li>`
                    ).join('')}
                </ul>
            `;
        }
        
        // Start polling when page loads
        startPolling();
        
        // Clean up on page unload
        window.addEventListener('beforeunload', stopPolling);
    </script>
</body>
</html>
```

---

## Acceptance Criteria

### ✓ Progress Polling
- [ ] Client polls `GET /queries/upload-jobs/{jobId}` successfully
- [ ] Polling interval is appropriate (1-2 seconds recommended)
- [ ] Client stops polling when job reaches terminal state
- [ ] Error handling implemented with appropriate retry logic
- [ ] UI updates smoothly with polling results

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
1. **Test** end-to-end: upload → progress polling → query API
2. **Verify** polling works reliably for concurrent uploads
3. **Benchmark** query performance under load (should handle frequent polling)
4. **Optimize** database queries for polling use case if needed
5. **Proceed** to Phase 5: Observability &amp; Production Readiness

---

## References

- **AWS S3 Presigned URLs**: https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html
- **Spring Data JPA Pagination**: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods.query-creation
- **HTTP Polling Best Practices**: https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API

---

**Phase 4 Complete** → Ready for Phase 5 (Observability &amp; Production Readiness)

