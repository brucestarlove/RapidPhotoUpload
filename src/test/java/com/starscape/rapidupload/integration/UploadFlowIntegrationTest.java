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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete upload flow.
 * Tests: create job → upload to S3 → process via SQS → verify completion
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestAwsConfig.class)
public class UploadFlowIntegrationTest extends BaseIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private PhotoRepository photoRepository;
    
    private String authToken;
    private String userId;
    
    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        
        // Register with unique email per test to avoid conflicts
        String uniqueEmail = "test-" + System.nanoTime() + "@example.com";
        Map<String, String> registerRequest = Map.of(
            "email", uniqueEmail,
            "password", "password123"
        );
        
        Map<String, Object> registerResponse = given()
                .contentType(ContentType.JSON)
                .body(registerRequest)
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .as(Map.class);
        
        authToken = (String) registerResponse.get("token");
        userId = (String) registerResponse.get("userId");
    }
    
    @Test
    void shouldCompleteFullUploadFlow() throws Exception {
        // 1. Create upload job
        Map<String, Object> file1 = Map.of(
            "filename", "test1.jpg",
            "mimeType", "image/jpeg",
            "bytes", 1024000L
        );
        
        Map<String, Object> createJobRequest = Map.of(
            "files", List.of(file1),
            "strategy", "S3_PRESIGNED"
        );
        
        Map<String, Object> createJobResponse = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createJobRequest)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(201)
                .body("jobId", notNullValue())
                .body("items", hasSize(1))
                .body("items[0].photoId", notNullValue())
                .body("items[0].presignedUrl", notNullValue())
                .extract()
                .as(Map.class);
        
        String jobId = (String) createJobResponse.get("jobId");
        List<Map<String, Object>> items = (List<Map<String, Object>>) createJobResponse.get("items");
        String photoId = (String) items.get(0).get("photoId");
        String presignedUrl = (String) items.get(0).get("presignedUrl");
        
        // 2. Verify job status is QUEUED
        given()
                .header("Authorization", "Bearer " + authToken)
                .get("/queries/upload-jobs/" + jobId)
                .then()
                .statusCode(200)
                .body("status", equalTo("QUEUED"))
                .body("totalCount", equalTo(1))
                .body("completedCount", equalTo(0));
        
        // 3. Get photo to find S3 key
        Photo photo = photoRepository.findById(photoId).orElseThrow();
        assertNotNull(photo);
        assertEquals(PhotoStatus.QUEUED, photo.getStatus());
        
        // Construct S3 key the same way CreateUploadJobHandler does: env/userId/jobId/photoId.ext
        String extension = photo.getFilename().substring(photo.getFilename().lastIndexOf('.'));
        String s3Key = String.format("test/%s/%s/%s%s", 
            photo.getUserId(), photo.getJobId(), photo.getPhotoId(), extension);
        
        // 4. Create test image and upload to S3
        byte[] testImage = TestUtils.createTestImage(1920, 1080);
        String etag = TestUtils.calculateEtag(testImage);
        
        // Upload directly to LocalStack S3 (simulating client upload)
        uploadToS3(s3Key, testImage, "image/jpeg");
        
        // 5. Send S3 event message to SQS to trigger processing
        TestUtils.sendS3EventToSqs(sqsClient, queueUrl, TEST_BUCKET, s3Key, etag, testImage.length);
        
        // 6. Wait for processing to complete (with timeout)
        await().atMost(java.time.Duration.ofSeconds(30))
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Photo updatedPhoto = photoRepository.findById(photoId).orElseThrow();
                    assertEquals(PhotoStatus.COMPLETED, updatedPhoto.getStatus(), 
                        "Photo should be completed after processing");
                });
        
        // 7. Verify photo metadata via API
        Map<String, Object> photoMetadata = given()
                .header("Authorization", "Bearer " + authToken)
                .get("/queries/photos/" + photoId)
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("width", equalTo(1920))
                .body("height", equalTo(1080))
                .body("checksum", notNullValue())
                .body("exif", notNullValue())
                .extract()
                .as(Map.class);
        
        assertNotNull(photoMetadata.get("checksum"));
        assertNotNull(photoMetadata.get("exif"));
        
        // 8. Wait for job status to update (JobProgressAggregator runs on schedule)
        await().atMost(java.time.Duration.ofSeconds(10))
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() -> {
                    given()
                            .header("Authorization", "Bearer " + authToken)
                            .get("/queries/upload-jobs/" + jobId)
                            .then()
                            .statusCode(200)
                            .body("status", equalTo("COMPLETED"))
                            .body("totalCount", equalTo(1))
                            .body("completedCount", equalTo(1))
                            .body("failedCount", equalTo(0));
                });
        
        // 9. Verify thumbnails exist in S3
        // Thumbnail key format: env/userId/jobId/thumbnails/photoId_256.ext
        int lastSlash = s3Key.lastIndexOf('/');
        String basePath = s3Key.substring(0, lastSlash);
        String filename = s3Key.substring(lastSlash + 1);
        int lastDot = filename.lastIndexOf('.');
        String name = lastDot >= 0 ? filename.substring(0, lastDot) : filename;
        String ext = lastDot >= 0 ? filename.substring(lastDot) : "";
        
        String thumbnail256Key = basePath + "/thumbnails/" + name + "_256" + ext;
        String thumbnail1024Key = basePath + "/thumbnails/" + name + "_1024" + ext;
        
        // Check if thumbnails exist in S3 by listing objects
        boolean has256 = s3Client.listObjectsV2(b -> b.bucket(TEST_BUCKET).prefix(thumbnail256Key))
                .contents().stream().anyMatch(obj -> obj.key().equals(thumbnail256Key));
        boolean has1024 = s3Client.listObjectsV2(b -> b.bucket(TEST_BUCKET).prefix(thumbnail1024Key))
                .contents().stream().anyMatch(obj -> obj.key().equals(thumbnail1024Key));
        
        assertTrue(has256, "Thumbnail 256px should exist in S3");
        assertTrue(has1024, "Thumbnail 1024px should exist in S3");
    }
    
    @Test
    void shouldHandleMultiplePhotosInJob() throws Exception {
        // Create upload job with 3 photos
        Map<String, Object> file1 = Map.of(
            "filename", "test1.jpg",
            "mimeType", "image/jpeg",
            "bytes", 1024000L
        );
        Map<String, Object> file2 = Map.of(
            "filename", "test2.jpg",
            "mimeType", "image/jpeg",
            "bytes", 2048000L
        );
        Map<String, Object> file3 = Map.of(
            "filename", "test3.png",
            "mimeType", "image/png",
            "bytes", 1536000L
        );
        
        Map<String, Object> createJobRequest = Map.of(
            "files", List.of(file1, file2, file3),
            "strategy", "S3_PRESIGNED"
        );
        
        Map<String, Object> createJobResponse = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createJobRequest)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(201)
                .body("jobId", notNullValue())
                .body("items", hasSize(3))
                .extract()
                .as(Map.class);
        
        String jobId = (String) createJobResponse.get("jobId");
        List<Map<String, Object>> items = (List<Map<String, Object>>) createJobResponse.get("items");
        
        // Upload and process each photo
        for (Map<String, Object> item : items) {
            String photoId = (String) item.get("photoId");
            Photo photo = photoRepository.findById(photoId).orElseThrow();
            String extension = photo.getFilename().substring(photo.getFilename().lastIndexOf('.'));
            String s3Key = String.format("test/%s/%s/%s%s", 
                photo.getUserId(), photo.getJobId(), photo.getPhotoId(), extension);
            
            // Create and upload test image
            byte[] testImage;
            if (photo.getMimeType().equals("image/png")) {
                testImage = TestUtils.createTestPngImage(800, 600);
            } else {
                testImage = TestUtils.createTestImage(800, 600);
            }
            String etag = TestUtils.calculateEtag(testImage);
            
            uploadToS3(s3Key, testImage, photo.getMimeType());
            TestUtils.sendS3EventToSqs(sqsClient, queueUrl, TEST_BUCKET, s3Key, etag, testImage.length);
        }
        
        // Wait for all photos to complete
        await().atMost(java.time.Duration.ofSeconds(60))
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Map<String, Object> jobStatus = given()
                            .header("Authorization", "Bearer " + authToken)
                            .get("/queries/upload-jobs/" + jobId)
                            .then()
                            .statusCode(200)
                            .extract()
                            .as(Map.class);
                    
                    int completedCount = (Integer) jobStatus.get("completedCount");
                    assertEquals(3, completedCount, "All 3 photos should be completed");
                    assertEquals("COMPLETED", jobStatus.get("status"));
                });
    }
    
    @Test
    void shouldHandleFailedProcessing() throws Exception {
        // Create upload job
        Map<String, Object> file1 = Map.of(
            "filename", "invalid.jpg",
            "mimeType", "image/jpeg",
            "bytes", 1024L
        );
        
        Map<String, Object> createJobRequest = Map.of(
            "files", List.of(file1),
            "strategy", "S3_PRESIGNED"
        );
        
        Map<String, Object> createJobResponse = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createJobRequest)
                .post("/commands/upload-jobs")
                .then()
                .statusCode(201)
                .extract()
                .as(Map.class);
        
        String jobId = (String) createJobResponse.get("jobId");
        List<Map<String, Object>> items = (List<Map<String, Object>>) createJobResponse.get("items");
        String photoId = (String) items.get(0).get("photoId");
        
        Photo photo = photoRepository.findById(photoId).orElseThrow();
        String extension = photo.getFilename().substring(photo.getFilename().lastIndexOf('.'));
        String s3Key = String.format("test/%s/%s/%s%s", 
            photo.getUserId(), photo.getJobId(), photo.getPhotoId(), extension);
        
        // Upload invalid/corrupted image data
        byte[] invalidImage = "This is not a valid image".getBytes();
        String etag = TestUtils.calculateEtag(invalidImage);
        
        uploadToS3(s3Key, invalidImage, "image/jpeg");
        TestUtils.sendS3EventToSqs(sqsClient, queueUrl, TEST_BUCKET, s3Key, etag, invalidImage.length);
        
        // Wait for processing to fail
        await().atMost(java.time.Duration.ofSeconds(30))
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Photo updatedPhoto = photoRepository.findById(photoId).orElseThrow();
                    assertEquals(PhotoStatus.FAILED, updatedPhoto.getStatus(), 
                        "Photo should be failed after invalid image processing");
                    assertNotNull(updatedPhoto.getErrorMessage());
                });
        
        // Wait for job status to reflect failure (JobProgressAggregator runs on schedule)
        await().atMost(java.time.Duration.ofSeconds(10))
                .pollInterval(java.time.Duration.ofMillis(500))
                .untilAsserted(() -> {
                    given()
                            .header("Authorization", "Bearer " + authToken)
                            .get("/queries/upload-jobs/" + jobId)
                            .then()
                            .statusCode(200)
                            .body("failedCount", equalTo(1))
                            .body("completedCount", equalTo(0));
                });
    }
}

