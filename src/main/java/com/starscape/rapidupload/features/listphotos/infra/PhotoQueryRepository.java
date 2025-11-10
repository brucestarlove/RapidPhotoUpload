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
 * Supports pagination, filtering by status/tag, and search by filename.
 * All queries exclude soft-deleted photos (deletedAt IS NULL) by default.
 */
@Repository
public interface PhotoQueryRepository extends JpaRepository<Photo, String> {
    
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.deletedAt IS NULL")
    Page<Photo> findByUserId(String userId, Pageable pageable);
    
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.status = :status AND p.deletedAt IS NULL")
    Page<Photo> findByUserIdAndStatus(String userId, PhotoStatus status, Pageable pageable);
    
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND LOWER(p.filename) LIKE LOWER(CONCAT('%', :query, '%')) AND p.deletedAt IS NULL")
    Page<Photo> findByUserIdAndFilenameContaining(
        @Param("userId") String userId, 
        @Param("query") String query, 
        Pageable pageable);
    
    /**
     * Find photos by user ID and tag label.
     * Uses IN subquery to find photos with the specified tag.
     * Excludes soft-deleted photos.
     */
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.deletedAt IS NULL " +
           "AND p.photoId IN (SELECT pt.photoId FROM PhotoTag pt, Tag t " +
           "WHERE pt.tagId = t.tagId AND t.userId = :userId AND t.label = :tagLabel)")
    Page<Photo> findByUserIdAndTag(
        @Param("userId") String userId,
        @Param("tagLabel") String tagLabel,
        Pageable pageable);
    
    /**
     * Find photos by user ID, status, and tag label.
     * Excludes soft-deleted photos.
     */
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.status = :status AND p.deletedAt IS NULL " +
           "AND p.photoId IN (SELECT pt.photoId FROM PhotoTag pt, Tag t " +
           "WHERE pt.tagId = t.tagId AND t.userId = :userId AND t.label = :tagLabel)")
    Page<Photo> findByUserIdAndStatusAndTag(
        @Param("userId") String userId,
        @Param("status") PhotoStatus status,
        @Param("tagLabel") String tagLabel,
        Pageable pageable);
    
    /**
     * Find photos by user ID, tag label, and filename search.
     * Excludes soft-deleted photos.
     */
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.deletedAt IS NULL " +
           "AND LOWER(p.filename) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "AND p.photoId IN (SELECT pt.photoId FROM PhotoTag pt, Tag t " +
           "WHERE pt.tagId = t.tagId AND t.userId = :userId AND t.label = :tagLabel)")
    Page<Photo> findByUserIdAndTagAndFilenameContaining(
        @Param("userId") String userId,
        @Param("tagLabel") String tagLabel,
        @Param("query") String query,
        Pageable pageable);
    
    /**
     * Find photos by user ID, status, tag label, and filename search.
     * Excludes soft-deleted photos.
     */
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.status = :status AND p.deletedAt IS NULL " +
           "AND LOWER(p.filename) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "AND p.photoId IN (SELECT pt.photoId FROM PhotoTag pt, Tag t " +
           "WHERE pt.tagId = t.tagId AND t.userId = :userId AND t.label = :tagLabel)")
    Page<Photo> findByUserIdAndStatusAndTagAndFilenameContaining(
        @Param("userId") String userId,
        @Param("status") PhotoStatus status,
        @Param("tagLabel") String tagLabel,
        @Param("query") String query,
        Pageable pageable);
    
    /**
     * Find soft-deleted photos (trash) for a user.
     */
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.deletedAt IS NOT NULL")
    Page<Photo> findByUserIdAndDeletedAtIsNotNull(@Param("userId") String userId, Pageable pageable);
    
    /**
     * Find photos ready for permanent deletion (deleted more than 7 days ago).
     */
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.deletedAt IS NOT NULL AND p.deletedAt < :cutoffDate")
    Page<Photo> findByUserIdAndDeletedAtBefore(
        @Param("userId") String userId,
        @Param("cutoffDate") java.time.Instant cutoffDate,
        Pageable pageable);
}

