package com.starscape.rapidupload.features.uploadphoto.infra;

import com.starscape.rapidupload.features.uploadphoto.domain.UploadJob;
import com.starscape.rapidupload.features.uploadphoto.domain.UploadJobRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaUploadJobRepository extends JpaRepository<UploadJob, String>, UploadJobRepository {
    
    @Query("SELECT j FROM UploadJob j LEFT JOIN FETCH j.photos WHERE j.jobId = :jobId")
    Optional<UploadJob> findByIdWithPhotos(@Param("jobId") String jobId);
}

