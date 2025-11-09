package com.starscape.rapidupload.features.listphotos.app;

import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListItem;
import com.starscape.rapidupload.features.listphotos.api.dto.PhotoListResponse;
import com.starscape.rapidupload.features.uploadphoto.domain.Photo;
import com.starscape.rapidupload.features.uploadphoto.domain.PhotoStatus;
import com.starscape.rapidupload.features.listphotos.infra.PhotoQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handler for listing photos with pagination and filtering.
 * Supports filtering by status and searching by filename.
 */
@Service
public class ListPhotosHandler {
    
    private final PhotoQueryRepository photoQueryRepository;
    
    public ListPhotosHandler(PhotoQueryRepository photoQueryRepository) {
        this.photoQueryRepository = photoQueryRepository;
    }
    
    @Transactional(readOnly = true)
    public PhotoListResponse handle(
            String userId, 
            String tag, 
            String status, 
            String query, 
            int page, 
            int size) {
        
        // Limit page size
        size = Math.min(size, 100);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Photo> photoPage;
        
        if (status != null && !status.isBlank()) {
            try {
                PhotoStatus photoStatus = PhotoStatus.valueOf(status.toUpperCase());
                photoPage = photoQueryRepository.findByUserIdAndStatus(userId, photoStatus, pageable);
            } catch (IllegalArgumentException e) {
                // Invalid status, fall back to all photos
                photoPage = photoQueryRepository.findByUserId(userId, pageable);
            }
        } else if (query != null && !query.isBlank()) {
            photoPage = photoQueryRepository.findByUserIdAndFilenameContaining(userId, query, pageable);
        } else {
            photoPage = photoQueryRepository.findByUserId(userId, pageable);
        }
        
        List<PhotoListItem> items = photoPage.getContent().stream()
                .map(this::toListItem)
                .toList();
        
        return new PhotoListResponse(
            items,
            photoPage.getNumber(),
            photoPage.getSize(),
            photoPage.getTotalElements(),
            photoPage.getTotalPages()
        );
    }
    
    /**
     * Convert Photo entity to PhotoListItem DTO.
     */
    private PhotoListItem toListItem(Photo photo) {
        String thumbnailUrl = null;
        if (photo.getS3Key() != null) {
            // Return placeholder URL; client can fetch presigned URL separately
            thumbnailUrl = "/queries/photos/" + photo.getPhotoId() + "/thumbnail?size=256";
        }
        
        return new PhotoListItem(
            photo.getPhotoId(),
            photo.getFilename(),
            photo.getMimeType(),
            photo.getBytes(),
            photo.getStatus().name(),
            photo.getWidth(),
            photo.getHeight(),
            thumbnailUrl,
            photo.getCreatedAt()
        );
    }
}

