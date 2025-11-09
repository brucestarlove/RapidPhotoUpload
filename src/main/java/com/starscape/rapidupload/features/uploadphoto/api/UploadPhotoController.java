package com.starscape.rapidupload.features.uploadphoto.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.uploadphoto.api.dto.*;
import com.starscape.rapidupload.features.uploadphoto.app.CreateUploadJobHandler;
import com.starscape.rapidupload.features.uploadphoto.app.FinalizeMultipartUploadHandler;
import com.starscape.rapidupload.features.uploadphoto.app.UpdateProgressHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/commands")
public class UploadPhotoController {
    
    private final CreateUploadJobHandler createUploadJobHandler;
    private final UpdateProgressHandler updateProgressHandler;
    private final FinalizeMultipartUploadHandler finalizeMultipartUploadHandler;
    
    public UploadPhotoController(
            CreateUploadJobHandler createUploadJobHandler,
            UpdateProgressHandler updateProgressHandler,
            FinalizeMultipartUploadHandler finalizeMultipartUploadHandler) {
        this.createUploadJobHandler = createUploadJobHandler;
        this.updateProgressHandler = updateProgressHandler;
        this.finalizeMultipartUploadHandler = finalizeMultipartUploadHandler;
    }
    
    @PostMapping("/upload-jobs")
    public ResponseEntity<CreateUploadJobResponse> createUploadJob(
            @Valid @RequestBody CreateUploadJobRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CreateUploadJobResponse response = createUploadJobHandler.handle(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/upload/progress")
    public ResponseEntity<Void> updateProgress(
            @Valid @RequestBody UpdateProgressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        updateProgressHandler.handle(request, principal.getUserId());
        return ResponseEntity.accepted().build();
    }
    
    /**
     * Finalize a multipart upload after all parts have been uploaded.
     * This completes the S3 multipart upload and triggers processing.
     * 
     * @param photoId The photo ID
     * @param request The finalize request containing upload ID and completed parts with ETags
     * @param principal The authenticated user
     * @return 204 No Content on success
     */
    @PostMapping("/upload/{photoId}/finalize")
    public ResponseEntity<Void> finalizeMultipartUpload(
            @PathVariable String photoId,
            @Valid @RequestBody FinalizeMultipartUploadRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        finalizeMultipartUploadHandler.handle(photoId, principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }
}

