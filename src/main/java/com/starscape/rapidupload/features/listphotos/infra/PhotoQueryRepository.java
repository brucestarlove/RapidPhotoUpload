package com.starscape.rapidupload.features.listphotos.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-optimized repository for photo queries.
 * Supports pagination, filtering, and search.
 */
@Repository
public interface PhotoQueryRepository extends JpaRepository<Photo, String> {
    
    Page<Photo> findByUserId(String userId, Pageable pageable);
    
    Page<Photo> findByUserIdAndStatus(String userId, PhotoStatus status, Pageable pageable);
    
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND LOWER(p.filename) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Photo> findByUserIdAndFilenameContaining(
        @Param("userId") String userId, 
        @Param("query") String query, 
        Pageable pageable);
}

