package com.starscape.rapidupload.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Test configuration to override AWS client beans to use LocalStack.
 * This ensures the application uses LocalStack endpoints instead of real AWS.
 */
@TestConfiguration
public class TestAwsConfig {
    
    @Bean
    @Primary
    public S3Client s3Client() {
        // Return the client created in BaseIntegrationTest
        return BaseIntegrationTest.s3Client;
    }
    
    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                BaseIntegrationTest.localstack.getAccessKey(),
                BaseIntegrationTest.localstack.getSecretKey()
        );
        
        return S3Presigner.builder()
                .endpointOverride(BaseIntegrationTest.localstack.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(BaseIntegrationTest.localstack.getRegion()))
                .build();
    }
    
    @Bean
    @Primary
    public SqsClient sqsClient() {
        // Return the client created in BaseIntegrationTest
        return BaseIntegrationTest.sqsClient;
    }
    
    /**
     * Async SQS client for Spring Cloud AWS SQS listener.
     * The SQS listener uses async clients internally, so we need to configure this as well.
     */
    @Bean
    @Primary
    public SqsAsyncClient sqsAsyncClient() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                BaseIntegrationTest.localstack.getAccessKey(),
                BaseIntegrationTest.localstack.getSecretKey()
        );
        
        return SqsAsyncClient.builder()
                .endpointOverride(BaseIntegrationTest.localstack.getEndpointOverride(SQS))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(BaseIntegrationTest.localstack.getRegion()))
                .build();
    }
}

