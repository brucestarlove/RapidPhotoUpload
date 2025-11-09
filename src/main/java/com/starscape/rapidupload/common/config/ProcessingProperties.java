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
    private int maxImageDimension;
    private List<String> supportedFormats;
    
    public List<Integer> getThumbnailSizes() {
        return thumbnailSizes;
    }
    
    public void setThumbnailSizes(List<Integer> thumbnailSizes) {
        this.thumbnailSizes = thumbnailSizes;
    }
    
    public int getMaxImageDimension() {
        return maxImageDimension;
    }
    
    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }
    
    public List<String> getSupportedFormats() {
        return supportedFormats;
    }
    
    public void setSupportedFormats(List<String> supportedFormats) {
        this.supportedFormats = supportedFormats;
    }
}

