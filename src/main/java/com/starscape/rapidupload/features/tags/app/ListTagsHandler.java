package com.starscape.rapidupload.features.tags.app;

import com.starscape.rapidupload.features.tags.domain.Tag;
import com.starscape.rapidupload.features.tags.infra.JpaTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handler for listing all tags for a user.
 * Returns tags sorted alphabetically.
 */
@Service
public class ListTagsHandler {
    
    private final JpaTagRepository tagRepository;
    
    public ListTagsHandler(JpaTagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }
    
    @Transactional(readOnly = true)
    public List<String> handle(String userId) {
        // Use the sorted query method for better performance
        List<Tag> tags = tagRepository.findByUserIdOrderByLabelAsc(userId);
        return tags.stream()
                .map(Tag::getLabel)
                .toList();
    }
}

