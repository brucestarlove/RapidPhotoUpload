package com.starscape.rapidupload.features.uploadphoto.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class S3MultipartPresignService {
    
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final int presignDurationMinutes;
    
    // Minimum 5MB per part (AWS requirement), optimal 8-16MB
    private static final long DEFAULT_PART_SIZE = 8 * 1024 * 1024; // 8MB
    private static final long MULTIPART_THRESHOLD = 5 * 1024 * 1024; // 5MB
    
    public S3MultipartPresignService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.presign-duration-minutes}") int presignDurationMinutes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignDurationMinutes = presignDurationMinutes;
    }
    
    public boolean shouldUseMultipart(long fileSize) {
        return fileSize > MULTIPART_THRESHOLD;
    }
    
    public MultipartUploadInfo initiateMultipartUpload(
            String s3Key, 
            String contentType,
            long totalBytes) {
        
        // Create multipart upload using S3Client (cannot be presigned)
        CreateMultipartUploadRequest multipartRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build();
        
        CreateMultipartUploadResponse multipartResponse = s3Client.createMultipartUpload(multipartRequest);
        String uploadId = multipartResponse.uploadId();
        
        // Calculate number of parts
        int partCount = (int) Math.ceil((double) totalBytes / DEFAULT_PART_SIZE);
        
        // Generate presigned URLs for each part
        List<PartUploadUrl> partUrls = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            long partSize = Math.min(DEFAULT_PART_SIZE, totalBytes - (partNumber - 1) * DEFAULT_PART_SIZE);
            
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(partSize)
                    .build();
            
            UploadPartPresignRequest presignPartRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                    .uploadPartRequest(uploadPartRequest)
                    .build();
            
            PresignedUploadPartRequest presignedPart = s3Presigner.presignUploadPart(presignPartRequest);
            
            partUrls.add(new PartUploadUrl(
                partNumber,
                presignedPart.url().toString(),
                partSize
            ));
        }
        
        return new MultipartUploadInfo(
            uploadId,
            s3Key,
            bucket,
            DEFAULT_PART_SIZE,
            partUrls,
            presignDurationMinutes * 60
        );
    }
    
    public record MultipartUploadInfo(
        String uploadId,
        String key,
        String bucket,
        long partSize,
        List<PartUploadUrl> parts,
        int expiresInSeconds
    ) {}
    
    public record PartUploadUrl(
        int partNumber,
        String url,
        long size
    ) {}
}

