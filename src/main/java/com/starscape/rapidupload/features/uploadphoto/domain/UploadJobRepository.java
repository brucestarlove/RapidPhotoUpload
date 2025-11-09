package com.starscape.rapidupload.features.uploadphoto.domain;

import java.util.Optional;

public interface UploadJobRepository {
    UploadJob save(UploadJob job);
    Optional<UploadJob> findById(String jobId);
    Optional<UploadJob> findByIdWithPhotos(String jobId);
}

