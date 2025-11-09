package com.starscape.rapidupload.features.getphotometadata.app;

import com.starscape.rapidupload.common.exception.NotFoundException;
import com.starscape.rapidupload.features.getphotometadata.api.dto.JobStatusResponse;
import com.starscape.rapidupload.features.getphotometadata.api.dto.PhotoStatusItem;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handler for retrieving job status.
 * Returns job information with all photo statuses.
 */
@Service
public class GetJobStatusHandler {
    
    private final UploadJobRepository jobRepository;
    
    public GetJobStatusHandler(UploadJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }
    
    @Transactional(readOnly = true)
    public JobStatusResponse handle(String jobId, String userId) {
        UploadJob job = jobRepository.findByIdWithPhotos(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + jobId));
        
        // Verify ownership
        if (!job.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Job does not belong to user");
        }
        
        List<PhotoStatusItem> photoItems = job.getPhotos().stream()
                .map(p -> new PhotoStatusItem(
                    p.getPhotoId(),
                    p.getFilename(),
                    p.getStatus().name(),
                    p.getErrorMessage()
                ))
                .toList();
        
        return new JobStatusResponse(
            job.getJobId(),
            job.getStatus().name(),
            job.getTotalCount(),
            job.getCompletedCount(),
            job.getFailedCount(),
            job.getCancelledCount(),
            photoItems,
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }
}

