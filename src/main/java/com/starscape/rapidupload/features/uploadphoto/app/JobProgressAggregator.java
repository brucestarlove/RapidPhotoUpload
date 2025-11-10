package com.starscape.rapidupload.features.uploadphoto.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.common.outbox.OutboxEvent;
import com.starscape.rapidupload.common.outbox.OutboxEventRepository;
import com.starscape.rapidupload.features.trackprogress.app.ProgressBroadcaster;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobStatus;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoFailed;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoProcessingCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Aggregates photo processing events to update UploadJob progress.
 * Processes outbox events periodically and updates job status based on photo completion/failure.
 * 
 * Responsibilities:
 * - Reads unprocessed outbox events (PhotoProcessingCompleted, PhotoFailed)
 * - Updates UploadJob aggregate state (progress counts, status)
 * - Marks events as processed after successful handling
 * - Broadcasts WebSocket message when job completes
 * 
 * Note: This service focuses on domain state updates and job completion notifications.
 * For real-time photo-level progress broadcasts, see PhotoEventListener and JobEventListener
 * in the trackprogress feature.
 */
@Service
public class JobProgressAggregator {
    
    private static final Logger log = LoggerFactory.getLogger(JobProgressAggregator.class);
    
    private final OutboxEventRepository outboxRepository;
    private final UploadJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final ProgressBroadcaster progressBroadcaster;
    
    public JobProgressAggregator(
            OutboxEventRepository outboxRepository,
            UploadJobRepository jobRepository,
            ObjectMapper objectMapper,
            ProgressBroadcaster progressBroadcaster) {
        this.outboxRepository = outboxRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.progressBroadcaster = progressBroadcaster;
    }
    
    /**
     * Process outbox events every 5 seconds.
     * Updates UploadJob progress based on PhotoProcessingCompleted and PhotoFailed events.
     */
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
    
    /**
     * Process a single outbox event.
     * Only handles photo completion/failure events.
     */
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
    
    /**
     * Update UploadJob progress by recalculating counts from photos.
     * Detects job completion and broadcasts WebSocket message immediately.
     */
    private void updateJobProgress(String jobId) {
        UploadJob job = jobRepository.findByIdWithPhotos(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        
        // Track previous status to detect completion transition
        UploadJobStatus previousStatus = job.getStatus();
        
        job.updateProgress();
        jobRepository.save(job);
        
        UploadJobStatus newStatus = job.getStatus();
        
        log.info("Updated job progress: jobId={}, status={}, completed={}/{}, failed={}", 
            jobId, newStatus, job.getCompletedCount(), job.getTotalCount(), job.getFailedCount());
        
        // Broadcast WebSocket message when job transitions to COMPLETED or COMPLETED_WITH_ERRORS
        if ((newStatus == UploadJobStatus.COMPLETED || newStatus == UploadJobStatus.COMPLETED_WITH_ERRORS) 
            && previousStatus != newStatus) {
            // Send completion message with exact format required by frontend
            progressBroadcaster.broadcastJobCompletion(
                job.getJobId(),
                "COMPLETED",  // Frontend expects "COMPLETED" for both completion statuses
                job.getTotalCount(),
                job.getCompletedCount(),
                job.getFailedCount()
            );
        }
    }
}

