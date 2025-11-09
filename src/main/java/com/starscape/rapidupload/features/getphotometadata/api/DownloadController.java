package com.starscape.rapidupload.features.getphotometadata.api;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Map;

/**
 * Controller for generating presigned download URLs for photos and thumbnails.
 * Provides secure, time-limited access to S3 objects.
 */
@RestController
@RequestMapping("/queries/photos")
public class DownloadController {
    
    private final PhotoRepository photoRepository;
    private final S3Presigner s3Presigner;
    private final String bucket;
    
    public DownloadController(
            PhotoRepository photoRepository,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket) {
        this.photoRepository = photoRepository;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }
    
    @GetMapping("/{photoId}/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        if (photo.getS3Key() == null) {
            throw new IllegalStateException("Photo not yet uploaded");
        }
        
        String presignedUrl = generatePresignedGetUrl(photo.getS3Key(), photo.getFilename());
        
        return ResponseEntity.ok(Map.of(
            "url", presignedUrl,
            "expiresIn", "300"  // 5 minutes
        ));
    }
    
    @GetMapping("/{photoId}/thumbnail")
    public ResponseEntity<Map<String, String>> getThumbnailUrl(
            @PathVariable String photoId,
            @RequestParam(defaultValue = "256") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        if (!photo.getUserId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }
        
        if (photo.getS3Key() == null) {
            throw new IllegalStateException("Photo not yet uploaded");
        }
        
        String thumbnailKey = getThumbnailKey(photo.getS3Key(), size);
        String presignedUrl = generatePresignedGetUrl(thumbnailKey, "thumbnail_" + photo.getFilename());
        
        return ResponseEntity.ok(Map.of(
            "url", presignedUrl,
            "expiresIn", "300"
        ));
    }
    
    /**
     * Generate thumbnail S3 key from original key.
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
    
    /**
     * Generate presigned GET URL for S3 object.
     */
    private String generatePresignedGetUrl(String s3Key, String filename) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .responseContentDisposition("attachment; filename=\"" + filename + "\"")
                .build();
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getRequest)
                .build();
        
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}

