package com.starscape.rapidupload.features.uploadphoto.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JpaPhotoRepository extends JpaRepository<Photo, String>, PhotoRepository {
    
    List<Photo> findByJobId(String jobId);
    
    Optional<Photo> findByS3Key(String s3Key);
    
    @Override
    @Query("SELECT p FROM Photo p WHERE p.userId = :userId AND p.deletedAt IS NOT NULL AND p.deletedAt < :cutoffDate")
    List<Photo> findByUserIdAndDeletedAtBefore(@Param("userId") String userId, @Param("cutoffDate") Instant cutoffDate);
}

