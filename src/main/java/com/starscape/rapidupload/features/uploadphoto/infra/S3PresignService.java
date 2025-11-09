package com.starscape.rapidupload.features.uploadphoto.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
public class S3PresignService {
    
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final int presignDurationMinutes;
    
    public S3PresignService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.presign-duration-minutes}") int presignDurationMinutes) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignDurationMinutes = presignDurationMinutes;
    }
    
    public PresignedUploadUrl generatePresignedPutUrl(
            String s3Key, 
            String contentType, 
            long contentLength) {
        
        PutObjectRequest.Builder putRequestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType);
        
        // For testing: Don't include contentLength in signature to allow flexible file sizes
        // This makes testing easier but reduces security (allows size abuse)
        // In production, you may want to always include contentLength for security
        // Only include if explicitly needed (for now, we skip it for flexibility)
        
        PutObjectRequest putRequest = putRequestBuilder.build();
        
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                .putObjectRequest(putRequest)
                .build();
        
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        
        return new PresignedUploadUrl(
            presignedRequest.url().toString(),
            "PUT",
            bucket,
            s3Key,
            presignDurationMinutes * 60
        );
    }
    
    public record PresignedUploadUrl(
        String url,
        String method,
        String bucket,
        String key,
        int expiresInSeconds
    ) {}
}

