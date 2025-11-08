# Phase 5: Observability &amp; Production Readiness

**Status**: Production Hardening  
**Duration Estimate**: 3-4 weeks  
**Dependencies**: Phase 4 (Real-time Progress &amp; Query APIs)

---

## Overview

Transform the application from functional to production-ready by implementing comprehensive observability (metrics, tracing, logging), resilience patterns (rate limiting, circuit breakers), exhaustive testing (unit, integration, load), and automated CI/CD pipelines. This phase ensures the system can operate reliably at scale with 100+ concurrent uploads.

---

## Goals

1. Implement metrics collection with Micrometer and Prometheus
2. Add distributed tracing with OpenTelemetry and AWS X-Ray
3. Enhance logging with structured JSON and correlation IDs
4. Implement rate limiting and circuit breakers
5. Build comprehensive test suite (unit, integration, load)
6. Create CI/CD pipeline with GitHub Actions
7. Set up monitoring dashboards in Grafana
8. Document deployment procedures

---

## Technical Stack

### New Dependencies

```xml
<!-- Observability -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- AWS X-Ray -->
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-xray</artifactId>
  <version>3.1.0</version>
</dependency>

<!-- Resilience -->
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
  <version>2.1.0</version>
</dependency>

<dependency>
  <groupId>com.github.vladimir-bukhtoyarov</groupId>
  <artifactId>bucket4j-core</artifactId>
  <version>8.7.0</version>
</dependency>

<!-- Structured Logging -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>

<!-- Testing -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <version>1.19.3</version>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <version>1.19.3</version>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>localstack</artifactId>
  <version>1.19.3</version>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>io.rest-assured</groupId>
  <artifactId>rest-assured</artifactId>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <scope>test</scope>
</dependency>
```

---

## Deliverables

### 1. Metrics Configuration

**`application.yml` additions**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active:dev}
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
        upload.processing.duration: true
        thumbnail.generation.duration: true

app:
  metrics:
    enabled: true
```

**`common/metrics/MetricsConfig.java`**
```java
package com.starscape.rapidupload.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            List.of(
                Tag.of("service", "rapidupload-api")
            )
        );
    }
}
```

**`features/uploadphoto/app/UploadMetrics.java`**
```java
package com.starscape.rapidupload.features.uploadphoto.app;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UploadMetrics {
    
    private final Counter jobsCreatedTotal;
    private final Counter photosCompletedTotal;
    private final Counter photosFailedTotal;
    private final AtomicInteger uploadsInFlight;
    private final Timer processingDuration;
    private final Timer thumbnailGenerationDuration;
    
    public UploadMetrics(MeterRegistry registry) {
        this.jobsCreatedTotal = Counter.builder("upload.job.created.total")
                .description("Total number of upload jobs created")
                .register(registry);
        
        this.photosCompletedTotal = Counter.builder("upload.photo.completed.total")
                .description("Total number of photos successfully completed")
                .register(registry);
        
        this.photosFailedTotal = Counter.builder("upload.photo.failed.total")
                .description("Total number of photos that failed processing")
                .register(registry);
        
        this.uploadsInFlight = new AtomicInteger(0);
        Gauge.builder("upload.inflight.gauge", uploadsInFlight, AtomicInteger::get)
                .description("Number of uploads currently in progress")
                .register(registry);
        
        this.processingDuration = Timer.builder("upload.processing.duration")
                .description("Time taken to process a photo (EXIF + thumbnails)")
                .register(registry);
        
        this.thumbnailGenerationDuration = Timer.builder("upload.thumbnail.generation.duration")
                .description("Time taken to generate thumbnails")
                .register(registry);
    }
    
    public void recordJobCreated() {
        jobsCreatedTotal.increment();
    }
    
    public void recordPhotoCompleted() {
        photosCompletedTotal.increment();
        uploadsInFlight.decrementAndGet();
    }
    
    public void recordPhotoFailed() {
        photosFailedTotal.increment();
        uploadsInFlight.decrementAndGet();
    }
    
    public void recordPhotoStarted() {
        uploadsInFlight.incrementAndGet();
    }
    
    public Timer.Sample startProcessingTimer() {
        return Timer.start();
    }
    
    public void recordProcessingDuration(Timer.Sample sample) {
        sample.stop(processingDuration);
    }
    
    public Timer.Sample startThumbnailTimer() {
        return Timer.start();
    }
    
    public void recordThumbnailDuration(Timer.Sample sample) {
        sample.stop(thumbnailGenerationDuration);
    }
}
```

**Update `PhotoProcessingService` to use metrics:**
```java
@Service
public class PhotoProcessingService {
    // ... existing fields ...
    private final UploadMetrics metrics;
    
    public PhotoProcessingService(
            // ... existing params ...
            UploadMetrics metrics) {
        // ... existing assignments ...
        this.metrics = metrics;
    }
    
    @Transactional
    public void processPhoto(String s3Key, String etag, long size) {
        metrics.recordPhotoStarted();
        Timer.Sample processingTimer = metrics.startProcessingTimer();
        
        try {
            // ... existing processing logic ...
            
            // Generate thumbnails with timing
            Timer.Sample thumbnailTimer = metrics.startThumbnailTimer();
            generateThumbnails(s3Key, imageBytes, photo.getMimeType());
            metrics.recordThumbnailDuration(thumbnailTimer);
            
            // ... mark completed ...
            metrics.recordPhotoCompleted();
            metrics.recordProcessingDuration(processingTimer);
            
        } catch (Exception e) {
            metrics.recordPhotoFailed();
            // ... existing error handling ...
        }
    }
}
```

---

### 2. Distributed Tracing Configuration

**`application.yml` additions**
```yaml
spring:
  application:
    name: rapidupload
    
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, reduce in prod (e.g., 0.1)
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

aws:
  xray:
    enabled: true
    daemon-address: ${AWS_XRAY_DAEMON_ADDRESS:127.0.0.1:2000}
```

**`common/config/TracingConfig.java`**
```java
package com.starscape.rapidupload.common.config;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class TracingConfig {
    
    /**
     * Filter to add trace ID to MDC for logging
     */
    @Bean
    public OncePerRequestFilter traceIdFilter(Tracer tracer) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                
                var span = tracer.currentSpan();
                if (span != null) {
                    String traceId = span.context().traceId();
                    MDC.put("traceId", traceId);
                    response.setHeader("X-Trace-Id", traceId);
                }
                
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove("traceId");
                }
            }
        };
    }
}
```

---

### 3. Structured Logging

**`src/main/resources/logback-spring.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId:-}] - %msg%n</pattern>
            </encoder>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>jobId</includeMdcKeyName>
                <includeMdcKeyName>photoId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <customFields>{"application":"rapidupload","environment":"${spring.profiles.active}"}</customFields>
            </encoder>
        </appender>
        
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>
    
    <logger name="com.starscape.rapidupload" level="DEBUG"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>
</configuration>
```

**MDC Utility for Correlation IDs:**
```java
package com.starscape.rapidupload.common.logging;

import org.slf4j.MDC;

public class CorrelationContext {
    
    private static final String JOB_ID = "jobId";
    private static final String PHOTO_ID = "photoId";
    private static final String USER_ID = "userId";
    
    public static void setJobId(String jobId) {
        MDC.put(JOB_ID, jobId);
    }
    
    public static void setPhotoId(String photoId) {
        MDC.put(PHOTO_ID, photoId);
    }
    
    public static void setUserId(String userId) {
        MDC.put(USER_ID, userId);
    }
    
    public static void clear() {
        MDC.remove(JOB_ID);
        MDC.remove(PHOTO_ID);
        MDC.remove(USER_ID);
    }
}
```

---

### 4. Rate Limiting

**`common/ratelimit/RateLimitService.java`**
```java
package com.starscape.rapidupload.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    // 10 upload jobs per minute per user
    private final Bandwidth jobCreationLimit = Bandwidth.classic(
        10, 
        Refill.intervally(10, Duration.ofMinutes(1))
    );
    
    // 20 progress updates per second per user
    private final Bandwidth progressUpdateLimit = Bandwidth.classic(
        20, 
        Refill.intervally(20, Duration.ofSeconds(1))
    );
    
    public boolean allowJobCreation(String userId) {
        Bucket bucket = buckets.computeIfAbsent(
            "job:" + userId, 
            k -> Bucket.builder().addLimit(jobCreationLimit).build()
        );
        return bucket.tryConsume(1);
    }
    
    public boolean allowProgressUpdate(String userId) {
        Bucket bucket = buckets.computeIfAbsent(
            "progress:" + userId, 
            k -> Bucket.builder().addLimit(progressUpdateLimit).build()
        );
        return bucket.tryConsume(1);
    }
}
```

**Apply rate limiting in controllers:**
```java
@PostMapping("/upload-jobs")
public ResponseEntity<CreateUploadJobResponse> createUploadJob(
        @Valid @RequestBody CreateUploadJobRequest request,
        @AuthenticationPrincipal UserPrincipal principal) {
    
    if (!rateLimitService.allowJobCreation(principal.getUserId())) {
        throw new RateLimitExceededException("Too many upload jobs. Please try again later.");
    }
    
    CreateUploadJobResponse response = createUploadJobHandler.handle(request, principal.getUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

---

### 5. Circuit Breakers

**`common/config/ResilienceConfig.java`**
```java
package com.starscape.rapidupload.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
        
        return CircuitBreakerRegistry.of(config);
    }
    
    @Bean
    public TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
    }
}
```

**Apply circuit breaker to S3 operations:**
```java
@Service
public class S3PresignService {
    
    private final CircuitBreaker circuitBreaker;
    
    public S3PresignService(
            S3Presigner s3Presigner,
            CircuitBreakerRegistry circuitBreakerRegistry,
            // ... other params
            ) {
        // ... existing assignments ...
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("s3-presign");
    }
    
    public PresignedUploadUrl generatePresignedPutUrl(
            String s3Key, String contentType, long contentLength) {
        
        return circuitBreaker.executeSupplier(() -> {
            // ... existing presign logic ...
        });
    }
}
```

---

### 6. Integration Tests

**`src/test/java/com/starscape/rapidupload/integration/UploadFlowIntegrationTest.java`**
```java
package com.starscape.rapidupload.integration;

import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UploadFlowIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(S3, SQS);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.s3.bucket", () -> "test-bucket");
    }
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private PhotoRepository photoRepository;
    
    private String authToken;
    
    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        
        // Register and get JWT token
        authToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "test@example.com", "password", "password123"))
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .path("token");
    }
    
    @Test
    void shouldCompleteFullUploadFlow() {
        // 1. Create upload job
        Map<String, Object> createJobRequest = Map.of(
            "files", List.of(
                Map.of("filename", "test.jpg", "mimeType", "image/jpeg", "bytes", 1024000)
            ),
            "strategy", "S3_PRESIGNED"
        );
        
        String jobId = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createJobRequest)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(201)
                .body("jobId", notNullValue())
                .body("items", hasSize(1))
                .body("items[0].presignedUrl", notNullValue())
                .extract()
                .path("jobId");
        
        // 2. Verify job status
        given()
                .header("Authorization", "Bearer " + authToken)
                .get("/queries/upload-jobs/" + jobId)
                .then()
                .statusCode(200)
                .body("status", equalTo("QUEUED"))
                .body("totalCount", equalTo(1));
        
        // 3. Simulate S3 upload and processing
        String photoId = photoRepository.findByJobId(jobId).get(0).getPhotoId();
        
        // Mark as completed (simulate processing)
        Photo photo = photoRepository.findById(photoId).get();
        photo.markProcessing("test-key", "test-bucket", "test-etag");
        photo.markCompleted(1920, 1080, "{}", "checksum123");
        photoRepository.save(photo);
        
        // 4. Verify photo status
        await().atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    given()
                            .header("Authorization", "Bearer " + authToken)
                            .get("/queries/photos/" + photoId)
                            .then()
                            .statusCode(200)
                            .body("status", equalTo("COMPLETED"))
                            .body("width", equalTo(1920))
                            .body("height", equalTo(1080));
                });
    }
    
    @Test
    void shouldEnforceRateLimits() {
        // Create 11 jobs rapidly (limit is 10/min)
        for (int i = 0; i < 11; i++) {
            Map<String, Object> request = Map.of(
                "files", List.of(Map.of("filename", "test" + i + ".jpg", 
                    "mimeType", "image/jpeg", "bytes", 1024)),
                "strategy", "S3_PRESIGNED"
            );
            
            var response = given()
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(ContentType.JSON)
                    .body(request)
                    .post("/commands/upload-jobs");
            
            if (i < 10) {
                response.then().statusCode(201);
            } else {
                response.then().statusCode(429);  // Too Many Requests
            }
        }
    }
}
```

---

### 7. Load Testing

**`loadtest/k6-upload-test.js`**
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const failureRate = new Rate('failures');

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '2m', target: 100 },   // Ramp up to 100 users
    { duration: '1m', target: 100 },   // Hold at 100 users
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500'],  // 95% of requests under 500ms
    'failures': ['rate<0.1'],            // Less than 10% failure rate
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  // Register user and get token
  const registerRes = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({
      email: `loadtest-${Date.now()}@example.com`,
      password: 'password123'
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  return { token: registerRes.json('token') };
}

export default function (data) {
  const params = {
    headers: {
      'Authorization': `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    },
  };
  
  // Create upload job with 10 files
  const files = Array.from({ length: 10 }, (_, i) => ({
    filename: `photo${i}.jpg`,
    mimeType: 'image/jpeg',
    bytes: Math.floor(Math.random() * 5000000) + 1000000  // 1-6 MB
  }));
  
  const payload = JSON.stringify({
    files: files,
    strategy: 'S3_PRESIGNED'
  });
  
  const createJobRes = http.post(
    `${BASE_URL}/commands/upload-jobs`,
    payload,
    params
  );
  
  const success = check(createJobRes, {
    'status is 201': (r) => r.status === 201,
    'has jobId': (r) => r.json('jobId') !== undefined,
    'has presigned URLs': (r) => r.json('items').length === 10,
  });
  
  failureRate.add(!success);
  
  if (success) {
    const jobId = createJobRes.json('jobId');
    
    // Query job status
    const statusRes = http.get(
      `${BASE_URL}/queries/upload-jobs/${jobId}`,
      params
    );
    
    check(statusRes, {
      'status query is 200': (r) => r.status === 200,
      'job status is QUEUED': (r) => r.json('status') === 'QUEUED',
    });
  }
  
  sleep(1);
}
```

**Run load test:**
```bash
k6 run loadtest/k6-upload-test.js
```

---

### 8. CI/CD Pipeline

**`.github/workflows/ci-cd.yml`**
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: rapidupload-api
  ECS_SERVICE: rapidupload-service
  ECS_CLUSTER: rapidupload-cluster
  ECS_TASK_DEFINITION: .aws/task-definition.json

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:15-alpine
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run tests
        env:
          DB_HOST: localhost
          DB_PORT: 5432
          DB_NAME: testdb
          DB_USER: test
          DB_PASSWORD: test
        run: mvn clean verify
      
      - name: Build application
        run: mvn package -DskipTests
      
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: application-jar
          path: target/*.jar
  
  build-docker-image:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Download artifact
        uses: actions/download-artifact@v4
        with:
          name: application-jar
          path: target/
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2
      
      - name: Build, tag, and push image to ECR
        id: build-image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest
          echo "image=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT
      
      - name: Update ECS task definition
        id: task-def
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: ${{ env.ECS_TASK_DEFINITION }}
          container-name: rapidupload-api
          image: ${{ steps.build-image.outputs.image }}
      
      - name: Deploy to ECS
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        with:
          task-definition: ${{ steps.task-def.outputs.task-definition }}
          service: ${{ env.ECS_SERVICE }}
          cluster: ${{ env.ECS_CLUSTER }}
          wait-for-service-stability: true
  
  performance-test:
    needs: build-docker-image
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Run k6 load test
        uses: grafana/k6-action@v0.3.1
        with:
          filename: loadtest/k6-upload-test.js
        env:
          BASE_URL: ${{ secrets.STAGING_API_URL }}
```

---

### 9. Dockerfile

**`Dockerfile`**
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add application JAR
COPY target/*.jar app.jar

# Add AWS X-Ray daemon (optional)
RUN apk add --no-cache wget unzip && \
    wget https://s3.us-east-2.amazonaws.com/aws-xray-assets.us-east-2/xray-daemon/aws-xray-daemon-linux-3.x.zip && \
    unzip aws-xray-daemon-linux-3.x.zip && \
    cp xray /usr/local/bin/xray && \
    rm -rf aws-xray-daemon-linux-3.x.zip xray

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

### 10. Monitoring Dashboards

**Grafana Dashboard JSON (excerpt)**
```json
{
  "dashboard": {
    "title": "RapidPhotoUpload - Upload Health",
    "panels": [
      {
        "title": "Upload Jobs Created",
        "targets": [
          {
            "expr": "rate(upload_job_created_total[5m])"
          }
        ]
      },
      {
        "title": "Photos Completed vs Failed",
        "targets": [
          {
            "expr": "rate(upload_photo_completed_total[5m])",
            "legendFormat": "Completed"
          },
          {
            "expr": "rate(upload_photo_failed_total[5m])",
            "legendFormat": "Failed"
          }
        ]
      },
      {
        "title": "Uploads In Flight",
        "targets": [
          {
            "expr": "upload_inflight_gauge"
          }
        ]
      },
      {
        "title": "Processing Duration (p95)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(upload_processing_duration_bucket[5m]))"
          }
        ]
      },
      {
        "title": "API Response Time (p95)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))"
          }
        ]
      },
      {
        "title": "SQS Queue Depth",
        "targets": [
          {
            "expr": "aws_sqs_approximate_number_of_messages_visible"
          }
        ]
      }
    ]
  }
}
```

---

### 11. Deployment Documentation

**`docs/DEPLOYMENT.md`**
```markdown
# RapidPhotoUpload Deployment Guide

## Prerequisites

- AWS Account with ECR, ECS, RDS, S3, SQS access
- Terraform 1.5+
- Docker
- kubectl (if using EKS)

## Infrastructure Setup

1. **Provision AWS Resources**
   ```bash
   cd infrastructure/terraform
   terraform init
   terraform plan -var-file=environments/prod.tfvars
   terraform apply -var-file=environments/prod.tfvars
   ```

2. **Create Database Schema**
   ```bash
   # Run Flyway migrations
   flyway -url=jdbc:postgresql://RDS_ENDPOINT:5432/rapidupload \
          -user=DB_USER -password=DB_PASSWORD migrate
   ```

3. **Build and Push Docker Image**
   ```bash
   mvn clean package
   docker build -t rapidupload-api:latest .
   docker tag rapidupload-api:latest ECR_REGISTRY/rapidupload-api:latest
   docker push ECR_REGISTRY/rapidupload-api:latest
   ```

4. **Deploy to ECS**
   ```bash
   aws ecs update-service \
       --cluster rapidupload-cluster \
       --service rapidupload-service \
       --force-new-deployment
   ```

## Environment Variables

Required environment variables for the application:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `AWS_REGION`, `S3_BUCKET`, `SQS_QUEUE_URL`
- `JWT_SECRET`
- `SPRING_PROFILES_ACTIVE=prod`

## Health Checks

- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Prometheus**: `GET /actuator/prometheus`

## Monitoring

- **CloudWatch**: Application logs and metrics
- **X-Ray**: Distributed tracing
- **Grafana**: Custom dashboards at https://grafana.example.com

## Rollback Procedure

1. Identify previous task definition version
2. Update ECS service:
   ```bash
   aws ecs update-service \
       --cluster rapidupload-cluster \
       --service rapidupload-service \
       --task-definition rapidupload-api:PREVIOUS_VERSION
   ```
```

---

## Acceptance Criteria

### ✓ Observability
- [ ] Prometheus metrics exposed at `/actuator/prometheus`
- [ ] Grafana dashboards display upload health, throughput, and errors
- [ ] X-Ray traces show end-to-end request flow (API → S3 → SQS → processor)
- [ ] JSON logs include correlation IDs (traceId, jobId, photoId, userId)

### ✓ Resilience
- [ ] Rate limiting enforced: 10 jobs/min/user, 20 progress/sec/user
- [ ] Circuit breaker protects S3 operations (opens after 50% failure rate)
- [ ] SQS DLQ captures failed messages for retry

### ✓ Testing
- [ ] Unit tests achieve >80% code coverage
- [ ] Integration tests pass with Testcontainers (Postgres + LocalStack)
- [ ] Load test: 100 concurrent users, 95% requests <500ms, <10% failure rate
- [ ] Contract tests verify API responses

### ✓ CI/CD
- [ ] GitHub Actions pipeline: build → test → Docker image → deploy to ECS
- [ ] Automated deployment to staging on merge to `main`
- [ ] Blue/green deployment with zero downtime
- [ ] Health checks pass before marking deployment successful

### ✓ Production Readiness
- [ ] Docker image built with health checks
- [ ] Application starts and serves traffic within 60 seconds
- [ ] Graceful shutdown handles WebSocket connections
- [ ] Database migrations run automatically on startup
- [ ] Secrets managed via AWS Secrets Manager

---

## Next Steps

Upon completion of Phase 5:
1. **Deploy** to production environment
2. **Monitor** metrics and traces in Grafana and X-Ray
3. **Perform** load testing with 100+ concurrent users
4. **Validate** SLAs: p95 latency <500ms, error rate <1%
5. **Document** operational runbooks and incident response

---

## References

- **Micrometer Documentation**: https://micrometer.io/docs
- **OpenTelemetry**: https://opentelemetry.io/docs/
- **Resilience4j**: https://resilience4j.readme.io/
- **Testcontainers**: https://www.testcontainers.org/
- **k6 Load Testing**: https://k6.io/docs/

---

**Phase 5 Complete** → **Production Ready! 🚀**

---

## Congratulations!

You have successfully implemented a production-grade, high-concurrency photo upload backend with:

✅ **DDD/CQRS/VSA Architecture**  
✅ **Async Processing (S3 → EventBridge → SQS)**  
✅ **Real-time Progress (WebSocket/STOMP)**  
✅ **Comprehensive Observability (Metrics, Tracing, Logging)**  
✅ **Resilience (Rate Limiting, Circuit Breakers)**  
✅ **Full Test Coverage (Unit, Integration, Load)**  
✅ **Automated CI/CD (GitHub Actions → ECS)**

The system is now ready to handle **100+ concurrent uploads** with real-time status updates, robust error handling, and production-grade monitoring.

