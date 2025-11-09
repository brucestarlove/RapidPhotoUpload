package com.starscape.rapidupload.features.uploadphoto.api.dto;

public record PhotoUploadItem(
    String photoId,
    String method,
    String presignedUrl,
    MultipartUploadInfo multipart
) {
    public static PhotoUploadItem singlePart(String photoId, String presignedUrl) {
        return new PhotoUploadItem(photoId, "PUT", presignedUrl, null);
    }
    
    public static PhotoUploadItem multipart(String photoId, MultipartUploadInfo multipart) {
        return new PhotoUploadItem(photoId, "MULTIPART", null, multipart);
    }
}

