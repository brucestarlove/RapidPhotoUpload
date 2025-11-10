package com.starscape.rapidupload.features.tags.infra;

import com.starscape.rapidupload.features.tags.domain.PhotoTag;
import com.starscape.rapidupload.features.tags.domain.PhotoTagId;
import com.starscape.rapidupload.features.tags.domain.PhotoTagRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA repository implementation for PhotoTag junction entity.
 * Spring Data JPA automatically provides implementations for methods declared in PhotoTagRepository
 * that match JpaRepository methods (save, delete).
 */
@Repository
public interface JpaPhotoTagRepository extends JpaRepository<PhotoTag, PhotoTagId>, PhotoTagRepository {
    
    @Override
    List<PhotoTag> findByPhotoId(String photoId);
    
    @Override
    List<PhotoTag> findByTagId(String tagId);
    
    @Override
    boolean existsByPhotoIdAndTagId(String photoId, String tagId);
    
    @Override
    @Modifying
    @Transactional
    @Query("DELETE FROM PhotoTag pt WHERE pt.photoId = :photoId AND pt.tagId = :tagId")
    void deleteByPhotoIdAndTagId(@Param("photoId") String photoId, @Param("tagId") String tagId);
}

