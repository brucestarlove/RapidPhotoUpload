package com.starscape.rapidupload.features.uploadphoto.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.uploadphoto.api.dto.*;
import com.starscape.rapidupload.features.uploadphoto.app.CreateUploadJobHandler;
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
    
    public UploadPhotoController(
            CreateUploadJobHandler createUploadJobHandler,
            UpdateProgressHandler updateProgressHandler) {
        this.createUploadJobHandler = createUploadJobHandler;
        this.updateProgressHandler = updateProgressHandler;
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
}

