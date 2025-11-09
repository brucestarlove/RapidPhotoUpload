package com.starscape.rapidupload.features.uploadphoto.app;

import com.starscape.rapidupload.common.exception.BusinessException;
import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.uploadphoto.api.dto.FinalizeMultipartUploadRequest;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import com.starscape.rapidupload.features.uploadphoto.infra.S3MultipartPresignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for finalizing multipart uploads.
 * Completes the S3 multipart upload and updates the photo status.
 */
@Service
public class FinalizeMultipartUploadHandler {
    
    private static final Logger log = LoggerFactory.getLogger(FinalizeMultipartUploadHandler.class);
    
    private final PhotoRepository photoRepository;
    private final S3MultipartPresignService multipartService;
    private final String bucket;
    private final String environment;
    
    public FinalizeMultipartUploadHandler(
            PhotoRepository photoRepository,
            S3MultipartPresignService multipartService,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${spring.profiles.active:dev}") String environment) {
        this.photoRepository = photoRepository;
        this.multipartService = multipartService;
        this.bucket = bucket;
        // Handle multiple profiles (comma-separated) by taking the first one
        this.environment = environment != null && environment.contains(",") 
            ? environment.split(",")[0].trim() 
            : (environment != null ? environment : "dev");
    }
    
    /**
     * Finalize a multipart upload for a photo.
     * 
     * @param photoId The photo ID
     * @param userId The user ID (for authorization)
     * @param request The finalize request containing upload ID and completed parts
     */
    @Transactional
    public void handle(String photoId, String userId, FinalizeMultipartUploadRequest request) {
        log.debug("Finalizing multipart upload for photo {} with uploadId {}", photoId, request.uploadId());
        
        // Find photo
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        
        // Verify ownership
        if (!photo.getUserId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED", "Photo does not belong to user");
        }
        
        // Verify photo is in a valid state for finalization
        if (photo.getStatus() != PhotoStatus.QUEUED && photo.getStatus() != PhotoStatus.UPLOADING) {
            throw new BusinessException(
                "INVALID_STATE", 
                String.format("Cannot finalize upload for photo in status: %s", photo.getStatus())
            );
        }
        
        try {
            // Construct S3 key: env/userId/jobId/photoId.ext
            String extension = extractExtension(photo.getFilename());
            String s3Key = String.format("%s/%s/%s/%s%s", 
                environment, userId, photo.getJobId(), photoId, extension);
            
            // Convert request parts to service parts
            List<S3MultipartPresignService.PartWithEtag> parts = request.parts().stream()
                    .map(part -> new S3MultipartPresignService.PartWithEtag(
                        part.partNumber(),
                        part.etag()
                    ))
                    .collect(Collectors.toList());
            
            // Complete the multipart upload in S3
            String etag = multipartService.completeMultipartUpload(s3Key, request.uploadId(), parts);
            
            // Update photo with S3 key and ETag, mark as processing
            // The S3 event listener will pick it up and process it
            photo.markProcessing(s3Key, bucket, etag);
            photoRepository.save(photo);
            
            log.info("Successfully finalized multipart upload for photo {}: s3Key={}, etag={}", 
                photoId, s3Key, etag);
            
        } catch (Exception e) {
            log.error("Failed to finalize multipart upload for photo {}: {}", photoId, e.getMessage(), e);
            photo.markFailed("Failed to finalize multipart upload: " + e.getMessage());
            photoRepository.save(photo);
            throw new BusinessException("FINALIZE_FAILED", 
                "Failed to finalize multipart upload: " + e.getMessage());
        }
    }
    
    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }
}

