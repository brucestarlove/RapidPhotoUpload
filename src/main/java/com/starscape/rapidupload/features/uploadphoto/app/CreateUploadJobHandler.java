package com.starscape.rapidupload.features.uploadphoto.app;

import com.starscape.rapidupload.common.outbox.OutboxService;
import com.starscape.rapidupload.features.uploadphoto.api.dto.*;
import com.starscape.rapidupload.features.uploadphoto.domain.*;
import com.starscape.rapidupload.features.uploadphoto.infra.S3MultipartPresignService;
import com.starscape.rapidupload.features.uploadphoto.infra.S3PresignService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CreateUploadJobHandler {
    
    private final UploadJobRepository uploadJobRepository;
    private final PhotoRepository photoRepository;
    private final S3PresignService s3PresignService;
    private final S3MultipartPresignService s3MultipartPresignService;
    private final OutboxService outboxService;
    private final String environment;
    
    public CreateUploadJobHandler(
            UploadJobRepository uploadJobRepository,
            PhotoRepository photoRepository,
            S3PresignService s3PresignService,
            S3MultipartPresignService s3MultipartPresignService,
            OutboxService outboxService,
            @Value("${spring.profiles.active:dev}") String environment) {
        this.uploadJobRepository = uploadJobRepository;
        this.photoRepository = photoRepository;
        this.s3PresignService = s3PresignService;
        this.s3MultipartPresignService = s3MultipartPresignService;
        this.outboxService = outboxService;
        // Handle multiple profiles (comma-separated) by taking the first one
        this.environment = environment != null && environment.contains(",") 
            ? environment.split(",")[0].trim() 
            : (environment != null ? environment : "dev");
    }
    
    @Transactional
    public CreateUploadJobResponse handle(CreateUploadJobRequest request, String userId) {
        // Create job
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "");
        UploadJob job = new UploadJob(jobId, userId, request.files().size());
        
        List<PhotoUploadItem> items = new ArrayList<>();
        
        // Create photos and generate presigned URLs
        for (FileUploadRequest fileRequest : request.files()) {
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
            if (s3MultipartPresignService.shouldUseMultipart(fileRequest.bytes()) && 
                request.strategy() == UploadStrategy.S3_MULTIPART) {
                
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
                // Single-part upload
                var presignedUrl = s3PresignService.generatePresignedPutUrl(
                    s3Key,
                    fileRequest.mimeType(),
                    fileRequest.bytes()
                );
                
                item = PhotoUploadItem.singlePart(photoId, presignedUrl.url());
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
        
        return new CreateUploadJobResponse(jobId, items);
    }
    
    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot) : "";
    }
}

