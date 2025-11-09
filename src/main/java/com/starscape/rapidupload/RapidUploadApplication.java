package com.starscape.rapidupload;

import com.starscape.rapidupload.common.config.ProcessingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProcessingProperties.class)
public class RapidUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(RapidUploadApplication.class, args);
    }
}

