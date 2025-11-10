package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.AggregateRoot;
import com.starscape.rapidupload.features.uploadphoto.domain.events.PhotoQueued;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "photos")
public class Photo extends AggregateRoot<String> {
    
    @Id
    @Column(name = "photo_id")
    private String photoId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private UploadJob uploadJob;
    
    @Column(name = "job_id", insertable = false, updatable = false)
    private String jobId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    
    @Column(nullable = false)
    private long bytes;
    
    @Column(name = "s3_key")
    private String s3Key;
    
    @Column(name = "s3_bucket")
    private String s3Bucket;
    
    @Column(name = "etag")
    private String etag;
    
    @Column(name = "checksum")
    private String checksum;
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exif_json", columnDefinition = "jsonb")
    private String exifJson;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStatus status;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    protected Photo() {
        // JPA constructor
    }
    
    public Photo(String photoId, String userId, String filename, String mimeType, long bytes) {
        super(photoId);
        validateInput(photoId, userId, filename, mimeType, bytes);
        
        this.photoId = photoId;
        this.userId = userId;
        this.filename = filename;
        this.mimeType = mimeType;
        this.bytes = bytes;
        this.status = PhotoStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        
        // Domain event
        registerEvent(new PhotoQueued(photoId, userId, filename, bytes, createdAt));
    }
    
    private void validateInput(String photoId, String userId, String filename, String mimeType, long bytes) {
        if (photoId == null || photoId.isBlank()) {
            throw new IllegalArgumentException("Photo ID cannot be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be blank");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("MIME type cannot be blank");
        }
        if (bytes <= 0) {
            throw new IllegalArgumentException("Bytes must be positive");
        }
    }
    
    @Override
    public String getId() {
        return photoId;
    }
    
    // Getters
    public String getPhotoId() { return photoId; }
    public String getJobId() { return jobId; }
    public String getUserId() { return userId; }
    public String getFilename() { return filename; }
    public String getMimeType() { return mimeType; }
    public long getBytes() { return bytes; }
    public String getS3Key() { return s3Key; }
    public String getS3Bucket() { return s3Bucket; }
    public String getEtag() { return etag; }
    public String getChecksum() { return checksum; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getExifJson() { return exifJson; }
    public PhotoStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    
    public boolean isDeleted() {
        return deletedAt != null;
    }
    
    public void setUploadJob(UploadJob uploadJob) {
        this.uploadJob = uploadJob;
        this.jobId = uploadJob.getJobId();
    }
    
    public void markUploading() {
        if (status == PhotoStatus.QUEUED) {
            this.status = PhotoStatus.UPLOADING;
            this.updatedAt = Instant.now();
        }
    }
    
    public void markProcessing(String s3Key, String s3Bucket, String etag) {
        if (status == PhotoStatus.UPLOADING || status == PhotoStatus.QUEUED) {
            this.s3Key = s3Key;
            this.s3Bucket = s3Bucket;
            this.etag = etag;
            this.status = PhotoStatus.PROCESSING;
            this.updatedAt = Instant.now();
        }
    }
    
    public void markCompleted(Integer width, Integer height, String exifJson, String checksum) {
        if (status == PhotoStatus.PROCESSING) {
            this.width = width;
            this.height = height;
            this.exifJson = exifJson;
            this.checksum = checksum;
            this.status = PhotoStatus.COMPLETED;
            this.completedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }
    
    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = PhotoStatus.FAILED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public void cancel() {
        if (status != PhotoStatus.COMPLETED && status != PhotoStatus.FAILED) {
            this.status = PhotoStatus.CANCELLED;
            this.completedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }
    
    /**
     * Mark photo as soft-deleted.
     * Sets deletedAt timestamp to current time.
     */
    public void markDeleted() {
        if (this.deletedAt == null) {
            this.deletedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }
    
    /**
     * Restore photo from soft-deleted state.
     * Clears deletedAt timestamp.
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
            this.updatedAt = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

