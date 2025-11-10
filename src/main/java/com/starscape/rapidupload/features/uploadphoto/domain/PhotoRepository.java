package com.starscape.rapidupload.features.uploadphoto.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository {
    Photo save(Photo photo);
    Optional<Photo> findById(String photoId);
    List<Photo> findByJobId(String jobId);
    Optional<Photo> findByS3Key(String s3Key);
    List<Photo> findByUserIdAndDeletedAtBefore(String userId, Instant cutoffDate);
    void delete(Photo photo);
}

