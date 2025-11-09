package com.starscape.rapidupload.features.uploadphoto.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

/**
 * Manual tool to reprocess messages from Dead Letter Queue (DLQ).
 * Can be invoked via admin endpoint or CLI.
 * 
 * This service allows administrators to manually reprocess failed messages
 * after investigating and fixing the root cause.
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
    
    /**
     * Reprocess messages from DLQ by sending them back to the main queue.
     * 
     * @param maxMessages Maximum number of messages to reprocess (max 10 per batch)
     * @return Number of messages successfully reprocessed
     */
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
        
        if (messages.isEmpty()) {
            log.info("No messages found in DLQ");
            return 0;
        }
        
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

