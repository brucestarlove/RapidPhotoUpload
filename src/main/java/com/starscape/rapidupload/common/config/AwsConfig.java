package com.starscape.rapidupload.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsConfig {
    
    @Value("${aws.region}")
    private String region;
    
    @Value("${aws.profile:}")
    private String profile;
    
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // Use profile if specified, otherwise use default credentials chain
        if (profile != null && !profile.isBlank()) {
            return ProfileCredentialsProvider.create(profile);
        }
        return DefaultCredentialsProvider.create();
    }
    
    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    public SqsClient sqsClient(AwsCredentialsProvider credentialsProvider) {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}

