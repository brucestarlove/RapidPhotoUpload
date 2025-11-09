package com.starscape.rapidupload.features.uploadphoto.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaPhotoRepository extends JpaRepository<Photo, String>, PhotoRepository {
    
    List<Photo> findByJobId(String jobId);
    
    Optional<Photo> findByS3Key(String s3Key);
}

