package com.starscape.rapidupload.features.deletephoto.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.deletephoto.app.ListTrashHandler;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for querying deleted photos (trash view).
 * Provides read-only access to soft-deleted photos.
 */
@RestController
@RequestMapping("/queries/photos")
public class TrashQueryController {
    
    private final ListTrashHandler listTrashHandler;
    
    public TrashQueryController(ListTrashHandler listTrashHandler) {
        this.listTrashHandler = listTrashHandler;
    }
    
    /**
     * Get all soft-deleted photos for the authenticated user (trash view).
     * GET /queries/photos/trash
     */
    @GetMapping("/trash")
    public ResponseEntity<PhotoListResponse> listTrash(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PhotoListResponse response = listTrashHandler.handle(principal.getUserId(), page, size);
        return ResponseEntity.ok(response);
    }
}

