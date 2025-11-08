# Phase 3: Async Processing Pipeline

**Status**: Event-Driven Processing  
**Duration Estimate**: 2-3 weeks  
**Dependencies**: Phase 2 (Core Upload Flow)

---

## Overview

Build the asynchronous processing pipeline that reacts to S3 ObjectCreated events, extracts metadata (EXIF), generates thumbnails, computes checksums, and updates photo records. This phase decouples upload from processing, ensuring the system can handle burst concurrency (100+ uploads) without blocking the command API.

---

## Goals

1. Configure S3 Event Notifications → EventBridge → SQS
2. Implement SQS message consumer for S3 events
3. Extract EXIF metadata from uploaded images
4. Generate multiple thumbnail sizes (256px, 1024px)
5. Compute SHA-256 checksums
6. Update photo status: PROCESSING → COMPLETED or FAILED
7. Implement idempotency and retry logic
8. Set up Dead Letter Queue (DLQ) for failed messages

---

## Technical Stack

### New Dependencies

```xml
<!-- Spring Cloud AWS for SQS -->
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-sqs</artifactId>
  <version>3.1.0</version>
</dependency>

<!-- Metadata extraction (EXIF) -->
<dependency>
  <groupId>com.drewnoakes</groupId>
  <artifactId>metadata-extractor</artifactId>
  <version>2.19.0</version>
</dependency>

<!-- Thumbnailator for image resizing -->
<dependency>
  <groupId>net.coobird</groupId>
  <artifactId>thumbnailator</artifactId>
  <version>0.4.20</version>
</dependency>

<!-- Apache Commons Codec for checksums -->
<dependency>
  <groupId>commons-codec</groupId>
  <artifactId>commons-codec</artifactId>
</dependency>
```

---

## Deliverables

### 1. AWS Infrastructure Updates (Terraform)

**`infrastructure/terraform/processing.tf`**
```hcl
# SQS Queue for photo processing
resource "aws_sqs_queue" "photo_processing_dlq" {
  name                      = "${local.app_name}-photo-processing-dlq-${local.env}"
  message_retention_seconds = 1209600  # 14 days
  
  tags = local.common_tags
}

resource "aws_sqs_queue" "photo_processing" {
  name                       = "${local.app_name}-photo-processing-${local.env}"
  visibility_timeout_seconds = 300  # 5 minutes
  message_retention_seconds  = 345600  # 4 days
  receive_wait_time_seconds  = 20  # Long polling
  
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.photo_processing_dlq.arn
    maxReceiveCount     = 3
  })
  
  tags = local.common_tags
}

# SQS Queue Policy (allow S3 EventBridge to send)
resource "aws_sqs_queue_policy" "photo_processing" {
  queue_url = aws_sqs_queue.photo_processing.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.photo_processing.arn
      }
    ]
  })
}

# EventBridge Rule for S3 ObjectCreated events
resource "aws_cloudwatch_event_rule" "s3_object_created" {
  name        = "${local.app_name}-s3-object-created-${local.env}"
  description = "Capture S3 ObjectCreated events for photo processing"
  
  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [aws_s3_bucket.media.id]
      }
      object = {
        key = [{
          prefix = "${local.env}/"
        }]
      }
    }
  })
  
  tags = local.common_tags
}

resource "aws_cloudwatch_event_target" "sqs" {
  rule      = aws_cloudwatch_event_rule.s3_object_created.name
  target_id = "SendToSQS"
  arn       = aws_sqs_queue.photo_processing.arn
}

# Enable EventBridge notifications on S3 bucket
resource "aws_s3_bucket_notification" "media_eventbridge" {
  bucket      = aws_s3_bucket.media.id
  eventbridge = true
}

# IAM Policy for app to consume SQS
resource "aws_iam_role_policy" "app_sqs" {
  name = "${local.app_name}-sqs-policy"
  role = aws_iam_role.app.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = [
          aws_sqs_queue.photo_processing.arn,
          aws_sqs_queue.photo_processing_dlq.arn
        ]
      }
    ]
  })
}

# Outputs
output "sqs_queue_url" {
  value = aws_sqs_queue.photo_processing.url
}

output "sqs_queue_arn" {
  value = aws_sqs_queue.photo_processing.arn
}

output "sqs_dlq_url" {
  value = aws_sqs_queue.photo_processing_dlq.url
}
```

---

### 2. Application Configuration

**`application.yml` additions**
```yaml
aws:
  sqs:
    queue-url: ${SQS_QUEUE_URL:https://sqs.us-east-1.amazonaws.com/123456789/rapidupload-photo-processing-dev}
    
spring:
  cloud:
    aws:
      sqs:
        enabled: true
        listener:
          max-concurrent-messages: 10
          max-messages-per-poll: 10
          poll-timeout: 20
          
app:
  processing:
    thumbnail-sizes:
      - 256
      - 1024
    max-image-dimension: 8192
    supported-formats:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
```

---

### 3. S3 Event Message Models

**`features/uploadphoto/infra/events/S3EventMessage.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3EventMessage(
    @JsonProperty("version") String version,
    @JsonProperty("id") String id,
    @JsonProperty("detail-type") String detailType,
    @JsonProperty("source") String source,
    @JsonProperty("time") String time,
    @JsonProperty("detail") S3EventDetail detail
) {}
```

**`features/uploadphoto/infra/events/S3EventDetail.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3EventDetail(
    @JsonProperty("version") String version,
    @JsonProperty("bucket") S3Bucket bucket,
    @JsonProperty("object") S3Object object,
    @JsonProperty("request-id") String requestId,
    @JsonProperty("requester") String requester,
    @JsonProperty("source-ip-address") String sourceIpAddress,
    @JsonProperty("reason") String reason
) {}
```

**`features/uploadphoto/infra/events/S3Bucket.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3Bucket(
    @JsonProperty("name") String name
) {}
```

**`features/uploadphoto/infra/events/S3Object.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record S3Object(
    @JsonProperty("key") String key,
    @JsonProperty("size") long size,
    @JsonProperty("etag") String etag,
    @JsonProperty("sequencer") String sequencer
) {}
```

---

### 4. Photo Processing Service

**`features/uploadphoto/app/PhotoProcessingService.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.app;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.outbox.OutboxService;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoProcessingCompleted;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoFailed;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
public class PhotoProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingService.class);
    
    private final PhotoRepository photoRepository;
    private final S3Client s3Client;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final List<Integer> thumbnailSizes;
    
    public PhotoProcessingService(
            PhotoRepository photoRepository,
            S3Client s3Client,
            OutboxService outboxService,
            ObjectMapper objectMapper,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${app.processing.thumbnail-sizes}") List<Integer> thumbnailSizes) {
        this.photoRepository = photoRepository;
        this.s3Client = s3Client;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.thumbnailSizes = thumbnailSizes;
    }
    
    @Transactional
    public void processPhoto(String s3Key, String etag, long size) {
        log.info("Processing photo: s3Key={}, etag={}, size={}", s3Key, etag, size);
        
        // Find photo by S3 key
        Optional<Photo> photoOpt = photoRepository.findByS3Key(s3Key);
        if (photoOpt.isEmpty()) {
            log.warn("Photo not found for S3 key: {}", s3Key);
            return;
        }
        
        Photo photo = photoOpt.get();
        
        // Idempotency check: skip if already completed
        if (photo.getStatus() == com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus.COMPLETED) {
            log.info("Photo already processed: {}", photo.getPhotoId());
            return;
        }
        
        try {
            // Mark as processing
            photo.markProcessing(s3Key, bucket, etag);
            photoRepository.save(photo);
            
            // Download image from S3
            byte[] imageBytes = downloadFromS3(s3Key);
            
            // Compute checksum
            String checksum = DigestUtils.sha256Hex(imageBytes);
            
            // Extract EXIF metadata
            Map<String, Object> exifData = extractExif(imageBytes);
            String exifJson = objectMapper.writeValueAsString(exifData);
            
            // Read image dimensions
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IOException("Failed to read image");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Generate thumbnails
            generateThumbnails(s3Key, imageBytes, photo.getMimeType());
            
            // Mark completed
            photo.markCompleted(width, height, exifJson, checksum);
            photoRepository.save(photo);
            
            // Publish event
            PhotoProcessingCompleted event = new PhotoProcessingCompleted(
                photo.getPhotoId(),
                photo.getUserId(),
                photo.getJobId(),
                width,
                height,
                checksum,
                Instant.now()
            );
            outboxService.publish(event, "Photo");
            
            log.info("Photo processed successfully: {}", photo.getPhotoId());
            
        } catch (Exception e) {
            log.error("Failed to process photo: {}", photo.getPhotoId(), e);
            photo.markFailed(e.getMessage());
            photoRepository.save(photo);
            
            // Publish failure event
            PhotoFailed event = new PhotoFailed(
                photo.getPhotoId(),
                photo.getUserId(),
                photo.getJobId(),
                e.getMessage(),
                Instant.now()
            );
            outboxService.publish(event, "Photo");
        }
    }
    
    private byte[] downloadFromS3(String s3Key) throws IOException {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();
        
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
            return response.readAllBytes();
        }
    }
    
    private Map<String, Object> extractExif(byte[] imageBytes) {
        Map<String, Object> exifData = new HashMap<>();
        
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
            
            for (Directory directory : metadata.getDirectories()) {
                String directoryName = directory.getName();
                Map<String, String> tags = new HashMap<>();
                
                for (Tag tag : directory.getTags()) {
                    tags.put(tag.getTagName(), tag.getDescription());
                }
                
                if (!tags.isEmpty()) {
                    exifData.put(directoryName, tags);
                }
            }
            
        } catch (ImageProcessingException | IOException e) {
            log.warn("Failed to extract EXIF data", e);
            exifData.put("error", e.getMessage());
        }
        
        return exifData;
    }
    
    private void generateThumbnails(String originalKey, byte[] imageBytes, String mimeType) {
        for (int size : thumbnailSizes) {
            try {
                // Generate thumbnail
                ByteArrayOutputStream thumbOutput = new ByteArrayOutputStream();
                Thumbnails.of(new ByteArrayInputStream(imageBytes))
                        .size(size, size)
                        .outputFormat(getFormatFromMimeType(mimeType))
                        .toOutputStream(thumbOutput);
                
                byte[] thumbnailBytes = thumbOutput.toByteArray();
                
                // Upload to S3 under thumbnails/ prefix
                String thumbnailKey = getThumbnailKey(originalKey, size);
                uploadThumbnailToS3(thumbnailKey, thumbnailBytes, mimeType);
                
                log.debug("Generated thumbnail: size={}, key={}", size, thumbnailKey);
                
            } catch (IOException e) {
                log.error("Failed to generate thumbnail: size={}", size, e);
            }
        }
    }
    
    private String getThumbnailKey(String originalKey, int size) {
        // Original: env/userId/jobId/photoId.ext
        // Thumbnail: env/userId/jobId/thumbnails/photoId_256.ext
        int lastSlash = originalKey.lastIndexOf('/');
        String basePath = originalKey.substring(0, lastSlash);
        String filename = originalKey.substring(lastSlash + 1);
        
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot) : "";
        
        return basePath + "/thumbnails/" + name + "_" + size + ext;
    }
    
    private void uploadThumbnailToS3(String key, byte[] data, String mimeType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(mimeType)
                .contentLength((long) data.length)
                .build();
        
        s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));
    }
    
    private String getFormatFromMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
```

---

### 5. Domain Events (Processing)

**`features/uploadphoto/domain/events/PhotoProcessingCompleted.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

public record PhotoProcessingCompleted(
    String photoId,
    String userId,
    String jobId,
    int width,
    int height,
    String checksum,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "PhotoProcessingCompleted";
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

**`features/uploadphoto/domain/events/PhotoFailed.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.domain.events;

import com.starscape.rapidupload.common.domain.DomainEvent;
import java.time.Instant;

public record PhotoFailed(
    String photoId,
    String userId,
    String jobId,
    String errorMessage,
    Instant occurredOn
) implements DomainEvent {
    
    @Override
    public String getEventType() {
        return "PhotoFailed";
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

### 6. SQS Message Listener

**`features/uploadphoto/infra/S3EventListener.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.features.uploadphoto.app.PhotoProcessingService;
import com.starscape.rapidupload.features.uploadphoto.infra.events.S3EventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class S3EventListener {
    
    private static final Logger log = LoggerFactory.getLogger(S3EventListener.class);
    
    private final PhotoProcessingService processingService;
    private final ObjectMapper objectMapper;
    
    public S3EventListener(PhotoProcessingService processingService, ObjectMapper objectMapper) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }
    
    @SqsListener("${aws.sqs.queue-url}")
    public void handleS3Event(String message) {
        log.info("Received SQS message: {}", message);
        
        try {
            // Parse EventBridge message
            S3EventMessage event = objectMapper.readValue(message, S3EventMessage.class);
            
            if (!"Object Created".equals(event.detailType())) {
                log.warn("Ignoring non-ObjectCreated event: {}", event.detailType());
                return;
            }
            
            String s3Key = event.detail().object().key();
            String etag = event.detail().object().etag();
            long size = event.detail().object().size();
            
            // Skip thumbnail files
            if (s3Key.contains("/thumbnails/")) {
                log.debug("Skipping thumbnail file: {}", s3Key);
                return;
            }
            
            // Process the photo
            processingService.processPhoto(s3Key, etag, size);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse S3 event message", e);
            throw new RuntimeException("Invalid message format", e);
        } catch (Exception e) {
            log.error("Failed to process S3 event", e);
            throw new RuntimeException("Processing failed", e);
        }
    }
}
```

---

### 7. Job Progress Aggregator

This listener updates the `UploadJob` aggregate when photos complete/fail.

**`features/uploadphoto/app/JobProgressAggregator.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.outbox.OutboxEvent;
import com.starscape.rapidupload.common.outbox.OutboxEventRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoProcessingCompleted;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoFailed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobProgressAggregator {
    
    private static final Logger log = LoggerFactory.getLogger(JobProgressAggregator.class);
    
    private final OutboxEventRepository outboxRepository;
    private final UploadJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    
    public JobProgressAggregator(
            OutboxEventRepository outboxRepository,
            UploadJobRepository jobRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }
    
    @Scheduled(fixedDelay = 5000)  // Every 5 seconds
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> events = outboxRepository.findUnprocessedEventsWithLimit(100);
        
        if (events.isEmpty()) {
            return;
        }
        
        log.debug("Processing {} outbox events", events.size());
        
        for (OutboxEvent event : events) {
            try {
                processEvent(event);
                event.markProcessed();
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event: {}", event.getEventId(), e);
                // Leave event unprocessed for retry
            }
        }
    }
    
    private void processEvent(OutboxEvent event) throws JsonProcessingException {
        // Only handle photo completion/failure events
        if ("PhotoProcessingCompleted".equals(event.getEventType())) {
            PhotoProcessingCompleted photoEvent = objectMapper.readValue(
                event.getPayload(), PhotoProcessingCompleted.class);
            updateJobProgress(photoEvent.jobId());
            
        } else if ("PhotoFailed".equals(event.getEventType())) {
            PhotoFailed photoEvent = objectMapper.readValue(
                event.getPayload(), PhotoFailed.class);
            updateJobProgress(photoEvent.jobId());
        }
    }
    
    private void updateJobProgress(String jobId) {
        UploadJob job = jobRepository.findByIdWithPhotos(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
        
        job.updateProgress();
        jobRepository.save(job);
        
        log.info("Updated job progress: jobId={}, status={}, completed={}/{}", 
            jobId, job.getStatus(), job.getCompletedCount(), job.getTotalCount());
    }
}
```

---

### 8. Scheduled Outbox Relayer (Optional Enhancement)

For more reliable event processing, implement a dedicated outbox relayer that publishes events to external systems.

**`common/outbox/OutboxRelayerService.java`**
```java
package com.starscape.rapidupload.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Optional: Relay outbox events to external message brokers (Kafka, SNS, etc.)
 * For Phase 3, we process events internally via JobProgressAggregator.
 */
@Service
public class OutboxRelayerService {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayerService.class);
    
    private final OutboxEventRepository outboxRepository;
    
    public OutboxRelayerService(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }
    
    @Scheduled(fixedDelay = 10000)  // Every 10 seconds
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> events = outboxRepository.findUnprocessedEventsWithLimit(50);
        
        if (events.isEmpty()) {
            return;
        }
        
        log.debug("Relaying {} outbox events", events.size());
        
        for (OutboxEvent event : events) {
            try {
                // TODO: Publish to external system (SNS, Kafka, etc.)
                // For now, just log
                log.debug("Relaying event: type={}, aggregateId={}", 
                    event.getEventType(), event.getAggregateId());
                
                // Mark as processed
                event.markProcessed();
                outboxRepository.save(event);
                
            } catch (Exception e) {
                log.error("Failed to relay event: {}", event.getEventId(), e);
            }
        }
    }
}
```

---

### 9. Configuration for Scheduled Tasks

**`common/config/SchedulingConfig.java`**
```java
package com.starscape.rapidupload.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Enables @Scheduled annotations
}
```

---

### 10. Error Handling & Retry Strategy

**Key Principles:**
- **Idempotency**: Use `(photoId, s3ETag)` to prevent duplicate processing
- **SQS Redrive**: Failed messages retry up to 3 times (exponential backoff via visibility timeout)
- **DLQ**: Messages that fail 3 times go to Dead Letter Queue for manual inspection
- **Transactional Safety**: Database updates are transactional; failures don't corrupt state

**Manual DLQ Processing Tool (CLI)**

**`features/uploadphoto/app/DlqReprocessor.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

/**
 * Manual tool to reprocess messages from DLQ.
 * Can be invoked via admin endpoint or CLI.
 */
@Service
public class DlqReprocessor {
    
    private static final Logger log = LoggerFactory.getLogger(DlqReprocessor.class);
    
    private final SqsClient sqsClient;
    private final String dlqUrl;
    private final String mainQueueUrl;
    
    public DlqReprocessor(
            SqsClient sqsClient,
            @Value("${aws.sqs.dlq-url:}") String dlqUrl,
            @Value("${aws.sqs.queue-url}") String mainQueueUrl) {
        this.sqsClient = sqsClient;
        this.dlqUrl = dlqUrl;
        this.mainQueueUrl = mainQueueUrl;
    }
    
    public int reprocessDlqMessages(int maxMessages) {
        if (dlqUrl == null || dlqUrl.isBlank()) {
            throw new IllegalStateException("DLQ URL not configured");
        }
        
        log.info("Reprocessing up to {} messages from DLQ", maxMessages);
        
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(dlqUrl)
                .maxNumberOfMessages(Math.min(maxMessages, 10))
                .build();
        
        ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
        List<Message> messages = response.messages();
        
        int reprocessed = 0;
        for (Message message : messages) {
            try {
                // Send back to main queue
                SendMessageRequest sendRequest = SendMessageRequest.builder()
                        .queueUrl(mainQueueUrl)
                        .messageBody(message.body())
                        .build();
                
                sqsClient.sendMessage(sendRequest);
                
                // Delete from DLQ
                DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                        .queueUrl(dlqUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                
                sqsClient.deleteMessage(deleteRequest);
                
                reprocessed++;
                log.info("Reprocessed message: {}", message.messageId());
                
            } catch (Exception e) {
                log.error("Failed to reprocess message: {}", message.messageId(), e);
            }
        }
        
        log.info("Reprocessed {} messages from DLQ", reprocessed);
        return reprocessed;
    }
}
```

---

### 11. AWS SQS Client Configuration

**`common/config/AwsConfig.java` (additions)**
```java
@Bean
public SqsClient sqsClient() {
    return SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
}
```

---

## Testing Strategy

### Integration Tests with LocalStack

**`src/test/resources/application-test.yml`**
```yaml
aws:
  region: us-east-1
  s3:
    bucket: test-bucket
  sqs:
    queue-url: http://localhost:4566/000000000000/test-queue
    
spring:
  cloud:
    aws:
      sqs:
        enabled: true
```

**`features/uploadphoto/infra/S3EventListenerIntegrationTest.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.*;

@SpringBootTest
@Testcontainers
class S3EventListenerIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(S3, SQS);
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.s3.bucket", () -> "test-bucket");
        registry.add("aws.sqs.queue-url", () -> 
            localstack.getEndpointOverride(SQS).toString() + "/000000000000/test-queue");
    }
    
    @Autowired
    private S3EventListener listener;
    
    @Test
    void shouldProcessS3Event() {
        // TODO: Implement test
        // 1. Upload file to LocalStack S3
        // 2. Send SQS message
        // 3. Verify photo status updated
        // 4. Verify thumbnails generated
    }
}
```

---

## Acceptance Criteria

### ✓ AWS Infrastructure
- [ ] S3 Event Notification → EventBridge enabled
- [ ] EventBridge rule filters ObjectCreated events for bucket
- [ ] SQS queue receives S3 events within 2 seconds of upload
- [ ] DLQ configured with redrive policy (maxReceiveCount = 3)

### ✓ Message Processing
- [ ] SQS listener consumes messages successfully
- [ ] Photo status transitions: QUEUED → PROCESSING → COMPLETED
- [ ] Failed processing moves message to DLQ after 3 retries

### ✓ Metadata Extraction
- [ ] EXIF data extracted and stored in `photos.exif_json`
- [ ] Image dimensions (width, height) captured
- [ ] SHA-256 checksum computed and stored

### ✓ Thumbnail Generation
- [ ] Thumbnails generated for 256px and 1024px sizes
- [ ] Thumbnails uploaded to S3 under `thumbnails/` prefix
- [ ] Original aspect ratio preserved

### ✓ Idempotency
- [ ] Duplicate S3 events don't cause double-processing
- [ ] Processing skipped if photo already COMPLETED

### ✓ Job Progress
- [ ] `UploadJob` counts updated as photos complete/fail
- [ ] Job status transitions to COMPLETED when all photos terminal
- [ ] Job status COMPLETED_WITH_ERRORS if some photos failed

### ✓ Observability
- [ ] Logs include correlation IDs (photoId, jobId)
- [ ] Processing duration logged
- [ ] Errors captured with stack traces

---

## Next Steps

Upon completion of Phase 3:
1. **Test** end-to-end flow: upload → S3 event → processing → completion
2. **Verify** thumbnails and EXIF data in S3 and database
3. **Monitor** SQS metrics (messages in-flight, DLQ depth)
4. **Proceed** to Phase 4: Real-time Progress & Query APIs

---

## References

- **AWS EventBridge S3 Events**: https://docs.aws.amazon.com/AmazonS3/latest/userguide/EventBridge.html
- **Spring Cloud AWS SQS**: https://docs.awspring.io/spring-cloud-aws/docs/3.0.0/reference/html/index.html#sqs-integration
- **Metadata Extractor Library**: https://github.com/drewnoakes/metadata-extractor
- **Thumbnailator**: https://github.com/coobird/thumbnailator

---

**Phase 3 Complete** → Ready for Phase 4 (Real-time Progress & Query APIs)

