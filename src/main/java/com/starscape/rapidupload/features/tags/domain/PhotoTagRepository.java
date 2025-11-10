package com.starscape.rapidupload.features.tags.domain;

import java.util.List;

/**
 * Repository interface for PhotoTag junction entity.
 */
public interface PhotoTagRepository {
    PhotoTag save(PhotoTag photoTag);
    void delete(PhotoTag photoTag);
    void deleteByPhotoIdAndTagId(String photoId, String tagId);
    List<PhotoTag> findByPhotoId(String photoId);
    List<PhotoTag> findByTagId(String tagId);
    boolean existsByPhotoIdAndTagId(String photoId, String tagId);
}

