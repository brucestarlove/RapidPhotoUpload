package com.starscape.rapidupload.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starscape.rapidupload.features.uploadphoto.infra.events.S3Bucket;
import com.starscape.rapidupload.features.uploadphoto.infra.events.S3EventDetail;
import com.starscape.rapidupload.features.uploadphoto.infra.events.S3EventMessage;
import com.starscape.rapidupload.features.uploadphoto.infra.events.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Utility class for integration tests.
 * Provides helper methods for creating test images, SQS messages, etc.
 */
public class TestUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Create a simple test JPEG image with specified dimensions.
     */
    public static byte[] createTestImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Fill with a gradient for visual verification
        for (int y = 0; y < height; y++) {
            int colorValue = (int) (255 * ((double) y / height));
            g.setColor(new Color(colorValue, colorValue, colorValue));
            g.drawLine(0, y, width, y);
        }
        
        g.dispose();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
    
    /**
     * Create a test PNG image with specified dimensions.
     */
    public static byte[] createTestPngImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        // Fill with a simple pattern
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        
        g.dispose();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
    
    /**
     * Create an EventBridge-formatted S3 event message as JSON string.
     */
    public static String createS3EventMessage(String bucketName, String s3Key, String etag, long size) {
        try {
            S3EventMessage event = new S3EventMessage(
                    "0",
                    UUID.randomUUID().toString(),
                    "Object Created",
                    "aws.s3",
                    Instant.now().toString(),
                    new S3EventDetail(
                            "0",
                            new S3Bucket(bucketName),
                            new S3Object(s3Key, size, etag, UUID.randomUUID().toString()),
                            UUID.randomUUID().toString(),
                            "test-requester",
                            "127.0.0.1",
                            "PutObject"
                    )
            );
            
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create S3 event message", e);
        }
    }
    
    /**
     * Send an S3 event message to the SQS queue.
     */
    public static void sendS3EventToSqs(SqsClient sqsClient, String queueUrl, String bucketName, 
                                       String s3Key, String etag, long size) {
        String messageBody = createS3EventMessage(bucketName, s3Key, etag, size);
        
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();
        
        sqsClient.sendMessage(request);
    }
    
    /**
     * Calculate ETag for a byte array (simple MD5-based ETag simulation).
     * In real S3, ETags are MD5 hashes, but for testing we'll use a simple hash.
     */
    public static String calculateEtag(byte[] data) {
        // Simple hash for testing - in real S3 this would be MD5
        int hash = data.length;
        for (byte b : data) {
            hash = hash * 31 + b;
        }
        return String.format("\"%08x\"", hash);
    }
}

