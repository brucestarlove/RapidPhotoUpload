package com.starscape.rapidupload.features.uploadphoto.app;

import com.starscape.rapidupload.common.config.ProcessingProperties;
import com.starscape.rapidupload.common.exception.BusinessException;
import com.starscape.rapidupload.common.outbox.OutboxService;
import com.starscape.rapidupload.features.uploadphoto.api.dto.*;
import com.starscape.rapidupload.features.uploadphoto.domain.*;
import com.starscape.rapidupload.features.uploadphoto.infra.S3MultipartPresignService;
import com.starscape.rapidupload.features.uploadphoto.infra.S3PresignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CreateUploadJobHandler {
    
    private static final Logger log = LoggerFactory.getLogger(CreateUploadJobHandler.class);
    
    private final UploadJobRepository uploadJobRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignService s3PresignService;
    private final S3MultipartPresignService s3MultipartPresignService;
    private final OutboxService outboxService;
    private final ProcessingProperties processingProperties;
    private final String environment;
    
    public CreateUploadJobHandler(
            UploadJobRepository uploadJobRepository,
            PhotoRepository photoRepository,
            S3PresignService s3PresignService,
            S3MultipartPresignService s3MultipartPresignService,
            OutboxService outboxService,
            ProcessingProperties processingProperties,
            @Value("${spring.profiles.active:dev}") String environment) {
        this.uploadJobRepository = uploadJobRepository;
        this.photoRepository = photoRepository;
        this.s3PresignService = s3PresignService;
        this.s3MultipartPresignService = s3MultipartPresignService;
        this.outboxService = outboxService;
        this.processingProperties = processingProperties;
        // Handle multiple profiles (comma-separated) by taking the first one
        this.environment = environment != null && environment.contains(",") 
            ? environment.split(",")[0].trim() 
            : (environment != null ? environment : "dev");
    }
    
    @Transactional
    public CreateUploadJobResponse handle(CreateUploadJobRequest request, String userId) {
        log.debug("Creating upload job for user {} with {} files", userId, request.files().size());
        
        try {
            // Create job
            String jobId = "job_" + UUID.randomUUID().toString().replace("-", "");
            UploadJob job = new UploadJob(jobId, userId, request.files().size());
            
            List<PhotoUploadItem> items = new ArrayList<>();
            
            // Create photos and generate presigned URLs
            for (FileUploadRequest fileRequest : request.files()) {
                // Validate MIME type against supported formats
                if (!processingProperties.isSupportedFormat(fileRequest.mimeType())) {
                    throw new BusinessException(
                        "UNSUPPORTED_MIME_TYPE",
                        String.format("Unsupported MIME type: %s. Supported formats: %s", 
                            fileRequest.mimeType(), 
                            String.join(", ", processingProperties.getSupportedFormats()))
                    );
                }
                String photoId = "ph_" + UUID.randomUUID().toString().replace("-", "");
                Photo photo = new Photo(
                    photoId,
                    userId,
                    fileRequest.filename(),
                    fileRequest.mimeType(),
                    fileRequest.bytes()
                );
                
                job.addPhoto(photo);
                
                // Generate S3 key: env/userId/jobId/photoId.ext
                String extension = extractExtension(fileRequest.filename());
                String s3Key = String.format("%s/%s/%s/%s%s", 
                    environment, userId, jobId, photoId, extension);
                
                // Determine upload strategy
                PhotoUploadItem item;
                try {
                    if (s3MultipartPresignService.shouldUseMultipart(fileRequest.bytes()) && 
                        request.strategy() == UploadStrategy.S3_MULTIPART) {
                        
                        log.debug("Generating multipart presigned URLs for photo {} ({} bytes)", photoId, fileRequest.bytes());
                        // Multipart upload
                        var multipartInfo = s3MultipartPresignService.initiateMultipartUpload(
                            s3Key,
                            fileRequest.mimeType(),
                            fileRequest.bytes()
                        );
                        
                        List<PartUrl> partUrls = multipartInfo.parts().stream()
                            .map(p -> new PartUrl(p.partNumber(), p.url(), p.size()))
                            .toList();
                        
                        item = PhotoUploadItem.multipart(
                            photoId,
                            new MultipartUploadInfo(
                                multipartInfo.uploadId(),
                                multipartInfo.partSize(),
                                partUrls
                            )
                        );
                    } else {
                        log.debug("Generating single-part presigned URL for photo {} ({} bytes)", photoId, fileRequest.bytes());
                        // Single-part upload
                        var presignedUrl = s3PresignService.generatePresignedPutUrl(
                            s3Key,
                            fileRequest.mimeType(),
                            fileRequest.bytes()
                        );
                        
                        item = PhotoUploadItem.singlePart(photoId, presignedUrl.url());
                    }
                } catch (Exception e) {
                    log.error("Failed to generate presigned URL for photo {}: {}", photoId, e.getMessage(), e);
                    throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
                }
                
                items.add(item);
                
                // Publish photo event
                photo.getDomainEvents().forEach(event -> 
                    outboxService.publish(event, "Photo"));
            }
        
        // Save job (cascades to photos)
        uploadJobRepository.save(job);
        
        // Publish job events
        job.getDomainEvents().forEach(event -> 
            outboxService.publish(event, "UploadJob"));
        
        log.info("Successfully created upload job {} with {} photos", jobId, items.size());
        return new CreateUploadJobResponse(jobId, items);
        } catch (Exception e) {
            log.error("Failed to create upload job for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }
}

