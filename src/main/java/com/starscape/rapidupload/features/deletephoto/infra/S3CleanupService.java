package com.starscape.rapidupload.features.deletephoto.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.List;

/**
 * Service for cleaning up S3 objects (original photos and thumbnails).
 * Handles errors gracefully to ensure database cleanup proceeds even if S3 deletion fails.
 */
@Service
public class S3CleanupService {
    
    private static final Logger log = LoggerFactory.getLogger(S3CleanupService.class);
    
    private final S3Client s3Client;
    private final String bucket;
    
    public S3CleanupService(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }
    
    /**
     * Delete photo and its thumbnails from S3.
     * 
     * @param s3Key The S3 key of the original photo
     * @return true if all deletions succeeded, false otherwise
     */
    public boolean deletePhotoAndThumbnails(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            log.warn("S3 key is null or blank, skipping S3 cleanup");
            return false;
        }
        
        boolean allSucceeded = true;
        
        // Delete thumbnails first
        List<Integer> thumbnailSizes = List.of(256, 1024);
        for (int size : thumbnailSizes) {
            String thumbnailKey = getThumbnailKey(s3Key, size);
            if (!deleteS3Object(thumbnailKey)) {
                allSucceeded = false;
            }
        }
        
        // Delete original photo
        if (!deleteS3Object(s3Key)) {
            allSucceeded = false;
        }
        
        return allSucceeded;
    }
    
    /**
     * Delete a single S3 object.
     * 
     * @param key The S3 key to delete
     * @return true if deletion succeeded or object doesn't exist, false on error
     */
    private boolean deleteS3Object(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteRequest);
            log.info("Successfully deleted S3 object: bucket={}, key={}", bucket, key);
            return true;
            
        } catch (NoSuchKeyException e) {
            log.debug("S3 object does not exist (already deleted?): bucket={}, key={}", bucket, key);
            return true; // Consider this success - object doesn't exist
        } catch (Exception e) {
            log.error("Failed to delete S3 object: bucket={}, key={}", bucket, key, e);
            return false;
        }
    }
    
    /**
     * Generate thumbnail S3 key from original key.
     * Uses the same pattern as existing code in ListPhotosHandler and GetPhotoMetadataHandler.
     */
    private String getThumbnailKey(String originalKey, int size) {
        int lastSlash = originalKey.lastIndexOf('/');
        String basePath = originalKey.substring(0, lastSlash);
        String filename = originalKey.substring(lastSlash + 1);
        
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot) : "";
        
        return basePath + "/thumbnails/" + name + "_" + size + ext;
    }
}

