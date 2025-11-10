package com.starscape.rapidupload.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for photo processing.
 * Binds to app.processing.* properties from application.yml
 */
@ConfigurationProperties(prefix = "app.processing")
public class ProcessingProperties {
    
    private List<Integer> thumbnailSizes;
    private List<String> supportedFormats;
    
    public List<Integer> getThumbnailSizes() {
        return thumbnailSizes;
    }
    
    public void setThumbnailSizes(List<Integer> thumbnailSizes) {
        this.thumbnailSizes = thumbnailSizes;
    }
    
    public List<String> getSupportedFormats() {
        return supportedFormats;
    }
    
    public void setSupportedFormats(List<String> supportedFormats) {
        this.supportedFormats = supportedFormats;
    }
    
    /**
     * Check if a MIME type is supported.
     * Performs case-insensitive comparison.
     * @param mimeType The MIME type to check
     * @return true if the MIME type is in the supported formats list
     */
    public boolean isSupportedFormat(String mimeType) {
        if (mimeType == null || supportedFormats == null || supportedFormats.isEmpty()) {
            return false;
        }
        String normalizedMimeType = mimeType.toLowerCase().trim();
        return supportedFormats.stream()
                .map(format -> format.toLowerCase().trim())
                .anyMatch(format -> format.equals(normalizedMimeType));
    }
}

