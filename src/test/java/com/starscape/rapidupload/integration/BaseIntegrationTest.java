package com.starscape.rapidupload.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Base class for integration tests.
 * Sets up Testcontainers for PostgreSQL and LocalStack (S3 + SQS).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    public static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(S3, SQS);
    
    protected static final String TEST_BUCKET = "test-bucket";
    protected static final String TEST_QUEUE_NAME = "test-queue";
    
    protected static S3Client s3Client;
    protected static SqsClient sqsClient;
    protected static String queueUrl;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // AWS configuration for LocalStack
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.region.static", () -> localstack.getRegion());
        registry.add("spring.cloud.aws.credentials.access-key", () -> localstack.getAccessKey());
        registry.add("spring.cloud.aws.credentials.secret-key", () -> localstack.getSecretKey());
        registry.add("aws.s3.bucket", () -> TEST_BUCKET);
        
        // Configure LocalStack endpoints for Spring Cloud AWS
        registry.add("spring.cloud.aws.s3.endpoint", () -> localstack.getEndpointOverride(S3).toString());
        registry.add("spring.cloud.aws.sqs.endpoint", () -> localstack.getEndpointOverride(SQS).toString());
        
        // Initialize AWS clients for LocalStack
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                localstack.getAccessKey(),
                localstack.getSecretKey()
        );
        
        s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(localstack.getRegion()))
                .build();
        
        sqsClient = SqsClient.builder()
                .endpointOverride(localstack.getEndpointOverride(SQS))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(localstack.getRegion()))
                .build();
        
        // Create S3 bucket
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(TEST_BUCKET)
                    .build());
        } catch (Exception e) {
            // Bucket might already exist, ignore
        }
        
        // Create SQS queue
        try {
            sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(TEST_QUEUE_NAME)
                    .build());
            
            queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(TEST_QUEUE_NAME)
                    .build()).queueUrl();
            
            registry.add("aws.sqs.queue-url", () -> queueUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SQS queue", e);
        }
    }
    
    /**
     * Upload a file to LocalStack S3.
     */
    protected void uploadToS3(String key, byte[] content, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(TEST_BUCKET)
                .key(key)
                .contentType(contentType)
                .build();
        
        s3Client.putObject(putRequest, RequestBody.fromInputStream(
                new ByteArrayInputStream(content), content.length));
    }
    
    /**
     * Get the LocalStack endpoint URL for S3.
     */
    protected String getS3Endpoint() {
        return localstack.getEndpointOverride(S3).toString();
    }
    
    /**
     * Get the LocalStack endpoint URL for SQS.
     */
    protected String getSqsEndpoint() {
        return localstack.getEndpointOverride(SQS).toString();
    }
}

