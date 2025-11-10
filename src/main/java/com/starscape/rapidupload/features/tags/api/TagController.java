package com.starscape.rapidupload.features.tags.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.tags.api.dto.AddTagRequest;
import com.starscape.rapidupload.features.tags.api.dto.TagsResponse;
import com.starscape.rapidupload.features.tags.app.AddTagToPhotoHandler;
import com.starscape.rapidupload.features.tags.app.ListTagsHandler;
import com.starscape.rapidupload.features.tags.app.RemoveTagFromPhotoHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller for tag management operations.
 * Handles adding/removing tags from photos and listing all tags.
 */
@RestController
@RequestMapping("/commands/photos")
public class TagController {
    
    private final AddTagToPhotoHandler addTagHandler;
    private final RemoveTagFromPhotoHandler removeTagHandler;
    private final ListTagsHandler listTagsHandler;
    
    public TagController(
            AddTagToPhotoHandler addTagHandler,
            RemoveTagFromPhotoHandler removeTagHandler,
            ListTagsHandler listTagsHandler) {
        this.addTagHandler = addTagHandler;
        this.removeTagHandler = removeTagHandler;
        this.listTagsHandler = listTagsHandler;
    }
    
    /**
     * Add a tag to a photo.
     * POST /commands/photos/{photoId}/tags
     */
    @PostMapping("/{photoId}/tags")
    public ResponseEntity<Void> addTag(
            @PathVariable String photoId,
            @Valid @RequestBody AddTagRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        addTagHandler.handle(photoId, principal.getUserId(), request.tag());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    /**
     * Remove a tag from a photo.
     * DELETE /commands/photos/{photoId}/tags/{tag}
     */
    @DeleteMapping("/{photoId}/tags/{tag}")
    public ResponseEntity<Void> removeTag(
            @PathVariable String photoId,
            @PathVariable String tag,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        // URL-decode the tag name
        String decodedTag = URLDecoder.decode(tag, StandardCharsets.UTF_8);
        removeTagHandler.handle(photoId, principal.getUserId(), decodedTag);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

