package com.starscape.rapidupload.features.listphotos.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import com.starscape.rapidupload.features.listphotos.app.ListPhotosHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for listing photos with pagination and filtering.
 * Supports filtering by status and searching by filename.
 */
@RestController
@RequestMapping("/queries/photos")
public class PhotoListController {
    
    private final ListPhotosHandler listPhotosHandler;
    
    public PhotoListController(ListPhotosHandler listPhotosHandler) {
        this.listPhotosHandler = listPhotosHandler;
    }
    
    @GetMapping
    public ResponseEntity<PhotoListResponse> listPhotos(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PhotoListResponse response = listPhotosHandler.handle(
            principal.getUserId(), tag, status, q, page, size);
        return ResponseEntity.ok(response);
    }
}

