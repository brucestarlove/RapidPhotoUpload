package com.starscape.rapidupload.features.uploadphoto.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.common.outbox.OutboxEvent;
import com.starscape.rapidupload.common.outbox.OutboxEventRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
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
 */
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
     */
    private void updateJobProgress(String jobId) {
        UploadJob job = jobRepository.findByIdWithPhotos(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        
        job.updateProgress();
        jobRepository.save(job);
        
        log.info("Updated job progress: jobId={}, status={}, completed={}/{}, failed={}", 
            jobId, job.getStatus(), job.getCompletedCount(), job.getTotalCount(), job.getFailedCount());
    }
}

