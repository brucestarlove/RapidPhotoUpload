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

/**
 * Listens to job-related events and broadcasts job status updates via WebSocket.
 * Tracks jobs that have recent photo events and broadcasts their status.
 * 
 * Responsibilities:
 * - Monitors outbox events to identify jobs with recent photo activity
 * - Broadcasts job-level status updates (total, completed, failed counts)
 * - Uses caching to avoid excessive broadcasts for the same job
 * 
 * Architecture Note:
 * This complements PhotoEventListener (photo-level updates) and JobProgressAggregator
 * (domain state updates). All three services read from the same outbox, each serving
 * a different purpose in the CQRS architecture.
 */
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
    
    /**
     * Broadcast job updates every 3 seconds.
     * Finds jobs with recent photo events and broadcasts their status.
     */
    @Scheduled(fixedDelay = 3000)  // Every 3 seconds
    @Transactional(readOnly = true)
    public void broadcastJobUpdates() {
        // Find jobs that have recent photo events
        List<OutboxEvent> photoEvents = outboxRepository.findUnprocessedEventsWithLimit(100);
        
        Set<String> jobIds = new HashSet<>();
        for (OutboxEvent event : photoEvents) {
            if ("Photo".equals(event.getAggregateType())) {
                try {
                    String payload = event.getPayload();
                    // Extract jobId from payload (all photo events have it except PhotoQueued)
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
    
    /**
     * Broadcast job status update.
     */
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

