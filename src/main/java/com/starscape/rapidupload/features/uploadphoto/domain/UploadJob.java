package com.starscape.rapidupload.features.uploadphoto.domain;

import com.starscape.rapidupload.common.domain.AggregateRoot;
import com.starscape.rapidupload.features.uploadphoto.domain.events.UploadJobCreated;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "upload_jobs")
public class UploadJob extends AggregateRoot<String> {
    
    @Id
    @Column(name = "job_id")
    private String jobId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "total_count", nullable = false)
    private int totalCount;
    
    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;
    
    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;
    
    @Column(name = "cancelled_count", nullable = false)
    private int cancelledCount = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadJobStatus status;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @OneToMany(mappedBy = "uploadJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Photo> photos = new ArrayList<>();
    
    protected UploadJob() {
        // JPA constructor
    }
    
    public UploadJob(String jobId, String userId, int totalCount) {
        super(jobId);
        this.jobId = jobId;
        this.userId = userId;
        this.totalCount = totalCount;
        this.status = UploadJobStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        
        // Domain event
        registerEvent(new UploadJobCreated(jobId, userId, totalCount, createdAt));
    }
    
    @Override
    public String getId() {
        return jobId;
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public int getCompletedCount() {
        return completedCount;
    }
    
    public int getFailedCount() {
        return failedCount;
    }
    
    public int getCancelledCount() {
        return cancelledCount;
    }
    
    public UploadJobStatus getStatus() {
        return status;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public List<Photo> getPhotos() {
        return Collections.unmodifiableList(photos);
    }
    
    public void addPhoto(Photo photo) {
        photos.add(photo);
        photo.setUploadJob(this);
    }
    
    public void markInProgress() {
        if (status == UploadJobStatus.QUEUED) {
            this.status = UploadJobStatus.IN_PROGRESS;
            this.updatedAt = Instant.now();
        }
    }
    
    public void updateProgress() {
        long completed = photos.stream().filter(p -> p.getStatus() == PhotoStatus.COMPLETED).count();
        long failed = photos.stream().filter(p -> p.getStatus() == PhotoStatus.FAILED).count();
        long cancelled = photos.stream().filter(p -> p.getStatus() == PhotoStatus.CANCELLED).count();
        
        this.completedCount = (int) completed;
        this.failedCount = (int) failed;
        this.cancelledCount = (int) cancelled;
        this.updatedAt = Instant.now();
        
        // Check if job is complete
        int terminalCount = completedCount + failedCount + cancelledCount;
        if (terminalCount == totalCount) {
            if (failedCount == 0 && cancelledCount == 0) {
                this.status = UploadJobStatus.COMPLETED;
            } else if (completedCount > 0) {
                this.status = UploadJobStatus.COMPLETED_WITH_ERRORS;
            } else {
                this.status = UploadJobStatus.FAILED;
            }
        } else if (status == UploadJobStatus.QUEUED && terminalCount > 0) {
            this.status = UploadJobStatus.IN_PROGRESS;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

