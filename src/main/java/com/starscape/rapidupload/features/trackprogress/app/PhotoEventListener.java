package com.starscape.rapidupload.features.trackprogress.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.common.outbox.OutboxEvent;
import com.starscape.rapidupload.common.outbox.OutboxEventRepository;
import com.starscape.rapidupload.features.trackprogress.api.dto.ProgressUpdate;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoFailed;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoProcessingCompleted;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoQueued;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Listens to photo domain events and broadcasts progress updates via WebSocket.
 * Processes outbox events to send real-time updates to subscribed clients.
 */
@Service
public class PhotoEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(PhotoEventListener.class);
    
    private final OutboxEventRepository outboxRepository;
    private final PhotoRepository photoRepository;
    private final ProgressBroadcaster progressBroadcaster;
    private final ObjectMapper objectMapper;
    
    public PhotoEventListener(
            OutboxEventRepository outboxRepository,
            PhotoRepository photoRepository,
            ProgressBroadcaster progressBroadcaster,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.photoRepository = photoRepository;
        this.progressBroadcaster = progressBroadcaster;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process photo events every 2 seconds and broadcast progress updates.
     * Reads unprocessed events, broadcasts them, but doesn't mark as processed
     * (JobProgressAggregator handles that). This ensures events are broadcast
     * even if JobProgressAggregator runs first.
     */
    @Scheduled(fixedDelay = 2000)  // Every 2 seconds
    @Transactional(readOnly = true)
    public void processPhotoEvents() {
        // Read unprocessed events and broadcast them
        // Note: We don't mark them as processed here - JobProgressAggregator does that
        // This means events might be broadcast multiple times, but that's acceptable
        // for progress updates (idempotent)
        List<OutboxEvent> events = outboxRepository.findUnprocessedEventsWithLimit(50);
        
        if (events.isEmpty()) {
            return;
        }
        
        for (OutboxEvent event : events) {
            try {
                // Only broadcast photo-related events
                if ("Photo".equals(event.getAggregateType())) {
                    handleEvent(event);
                }
            } catch (Exception e) {
                log.error("Failed to broadcast event: {}", event.getEventId(), e);
            }
        }
    }
    
    /**
     * Handle a photo event and broadcast progress update.
     */
    private void handleEvent(OutboxEvent event) throws JsonProcessingException {
        switch (event.getEventType()) {
            case "PhotoQueued" -> {
                PhotoQueued photoEvent = objectMapper.readValue(event.getPayload(), PhotoQueued.class);
                // Look up photo to get jobId
                Photo photo = photoRepository.findById(photoEvent.photoId()).orElse(null);
                if (photo != null) {
                    ProgressUpdate update = ProgressUpdate.of(
                        photo.getJobId(),
                        photoEvent.photoId(),
                        "QUEUED",
                        0,
                        "Photo queued for upload"
                    );
                    progressBroadcaster.broadcastProgress(update);
                }
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
}

