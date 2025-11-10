package com.starscape.rapidupload.features.trackprogress.app;

import com.starscape.rapidupload.features.trackprogress.api.dto.JobStatusUpdate;
import com.starscape.rapidupload.features.trackprogress.api.dto.ProgressUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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
    
    /**
     * Broadcast job completion message with exact format required by frontend.
     * This message includes totalCount (so frontend recognizes it as JobStatusUpdate)
     * and does NOT include photoId (which would make it be classified as ProgressUpdate).
     * Also does NOT include cancelledCount per frontend requirements.
     * 
     * @param jobId The job ID
     * @param status The job status (should be "COMPLETED")
     * @param totalCount Total number of photos in the job
     * @param completedCount Number of completed photos
     * @param failedCount Number of failed photos
     */
    public void broadcastJobCompletion(String jobId, String status, int totalCount, 
                                       int completedCount, int failedCount) {
        String destination = "/topic/job/" + jobId;
        
        // Create message with exact format required by frontend
        Map<String, Object> message = new HashMap<>();
        message.put("jobId", jobId);
        message.put("status", status);
        message.put("totalCount", totalCount);
        message.put("completedCount", completedCount);
        message.put("failedCount", failedCount);
        message.put("timestamp", Instant.now().toString());
        
        messagingTemplate.convertAndSend(destination, message);
        log.info("Broadcasted job completion to {}: status={}, completed={}/{}, failed={}", 
            destination, status, completedCount, totalCount, failedCount);
    }
}

