package com.starscape.rapidupload.features.tags.api;

import com.starscape.rapidupload.common.security.UserPrincipal;
import com.starscape.rapidupload.features.tags.api.dto.TagsResponse;
import com.starscape.rapidupload.features.tags.app.ListTagsHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for querying tags.
 * Provides read-only access to tag information.
 */
@RestController
@RequestMapping("/queries/tags")
public class TagsQueryController {
    
    private final ListTagsHandler listTagsHandler;
    
    public TagsQueryController(ListTagsHandler listTagsHandler) {
        this.listTagsHandler = listTagsHandler;
    }
    
    /**
     * Get all tags for the authenticated user.
     * GET /queries/tags
     */
    @GetMapping
    public ResponseEntity<TagsResponse> listTags(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        var tags = listTagsHandler.handle(principal.getUserId());
        return ResponseEntity.ok(new TagsResponse(tags));
    }
}

