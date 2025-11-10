package com.starscape.rapidupload.features.tags.domain;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Tag domain entity.
 */
public interface TagRepository {
    Tag save(Tag tag);
    Optional<Tag> findById(String tagId);
    Optional<Tag> findByUserIdAndLabel(String userId, String label);
    List<Tag> findByUserId(String userId);
    void delete(Tag tag);
}

