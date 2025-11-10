package com.starscape.rapidupload.features.deletephoto.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.deletephoto.app.DeletePhotoHandler;
import com.starscape.rapidupload.features.deletephoto.app.PermanentDeleteHandler;
import com.starscape.rapidupload.features.deletephoto.app.RestorePhotoHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for photo deletion operations.
 * Handles soft delete, restore, and permanent deletion.
 */
@RestController
@RequestMapping("/commands/photos")
public class DeletePhotoController {
    
    private final DeletePhotoHandler deletePhotoHandler;
    private final RestorePhotoHandler restorePhotoHandler;
    private final PermanentDeleteHandler permanentDeleteHandler;
    
    public DeletePhotoController(
            DeletePhotoHandler deletePhotoHandler,
            RestorePhotoHandler restorePhotoHandler,
            PermanentDeleteHandler permanentDeleteHandler) {
        this.deletePhotoHandler = deletePhotoHandler;
        this.restorePhotoHandler = restorePhotoHandler;
        this.permanentDeleteHandler = permanentDeleteHandler;
    }
    
    /**
     * Soft delete a photo.
     * DELETE /commands/photos/{photoId}
     */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        deletePhotoHandler.handle(photoId, principal.getUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    /**
     * Restore a soft-deleted photo.
     * POST /commands/photos/{photoId}/restore
     */
    @PostMapping("/{photoId}/restore")
    public ResponseEntity<Void> restorePhoto(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        restorePhotoHandler.handle(photoId, principal.getUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    /**
     * Permanently delete a photo (only if deleted more than 7 days ago).
     * DELETE /commands/photos/{photoId}/permanent
     */
    @DeleteMapping("/{photoId}/permanent")
    public ResponseEntity<Void> permanentDeletePhoto(
            @PathVariable String photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        permanentDeleteHandler.handle(photoId, principal.getUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

