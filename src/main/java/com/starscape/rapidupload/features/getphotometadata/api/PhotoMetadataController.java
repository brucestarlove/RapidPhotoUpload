package com.starscape.rapidupload.features.getphotometadata.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.getphotometadata.api.dto.JobStatusResponse;
import com.starscape.rapidupload.features.getphotometadata.api.dto.PhotoMetadataResponse;
import com.starscape.rapidupload.features.getphotometadata.app.GetJobStatusHandler;
import com.starscape.rapidupload.features.getphotometadata.app.GetPhotoMetadataHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for querying photo metadata and job status.
 * Provides read-only access to photo and job information.
 */
@RestController
@RequestMapping("/queries")
public class PhotoMetadataController {
    
    private final GetPhotoMetadataHandler getPhotoMetadataHandler;
    private final GetJobStatusHandler getJobStatusHandler;
    
    public PhotoMetadataController(
            GetPhotoMetadataHandler getPhotoMetadataHandler,
            GetJobStatusHandler getJobStatusHandler) {
        this.getPhotoMetadataHandler = getPhotoMetadataHandler;
        this.getJobStatusHandler = getJobStatusHandler;
    }
    
    @GetMapping("/photos/{photoId}")
    public ResponseEntity<PhotoMetadataResponse> getPhotoMetadata(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PhotoMetadataResponse response = getPhotoMetadataHandler.handle(photoId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/upload-jobs/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        JobStatusResponse response = getJobStatusHandler.handle(jobId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}

