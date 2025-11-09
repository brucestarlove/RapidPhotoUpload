package com.starscape.rapidupload.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable Spring's scheduled task execution.
 * Required for @Scheduled annotations to work.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Enables @Scheduled annotations
}

