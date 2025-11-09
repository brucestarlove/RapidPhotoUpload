package com.starscape.rapidupload.features.uploadphoto.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.features.uploadphoto.app.PhotoProcessingService;
import com.starscape.rapidupload.features.uploadphoto.infra.events.S3EventMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Listens to SQS messages containing S3 ObjectCreated events from EventBridge.
 * Processes photos when they are uploaded to S3.
 * 
 * Only enabled when aws.sqs.queue-url is configured and spring.cloud.aws.sqs.enabled=true
 */
@Component
@ConditionalOnProperty(name = "spring.cloud.aws.sqs.enabled", havingValue = "true", matchIfMissing = false)
public class S3EventListener {
    
    private static final Logger log = LoggerFactory.getLogger(S3EventListener.class);
    
    private final PhotoProcessingService processingService;
    private final ObjectMapper objectMapper;
    
    public S3EventListener(PhotoProcessingService processingService, ObjectMapper objectMapper) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Handle SQS message containing S3 event.
     * The queue URL is configured via ${aws.sqs.queue-url} property.
     */
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
            
            // Skip thumbnail files to avoid infinite loops
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

