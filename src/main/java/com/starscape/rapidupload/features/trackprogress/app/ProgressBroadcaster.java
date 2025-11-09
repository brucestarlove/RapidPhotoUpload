package com.starscape.rapidupload.features.trackprogress.app;

import com.starscape.rapidupload.features.trackprogress.api.dto.JobStatusUpdate;
import com.starscape.rapidupload.features.trackprogress.api.dto.ProgressUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for broadcasting progress updates via WebSocket.
 * Sends real-time updates to subscribed clients.
 */
@Service
public class ProgressBroadcaster {
    
    private static final Logger log = LoggerFactory.getLogger(ProgressBroadcaster.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public ProgressBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Broadcast progress update to all subscribers of a job topic.
     */
    public void broadcastProgress(ProgressUpdate update) {
        String destination = "/topic/job/" + update.jobId();
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Broadcasted progress to {}: photoId={}, status={}", 
            destination, update.photoId(), update.status());
    }
    
    /**
     * Send progress update to a specific user.
     */
    public void sendToUser(String userId, ProgressUpdate update) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/progress", update);
        log.debug("Sent progress to user {}: photoId={}", userId, update.photoId());
    }
    
    /**
     * Broadcast job status update.
     */
    public void broadcastJobStatus(JobStatusUpdate update) {
        String destination = "/topic/job/" + update.jobId();
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Broadcasted job status to {}: status={}, completed={}/{}", 
            destination, update.status(), update.completedCount(), update.totalCount());
    }
}

