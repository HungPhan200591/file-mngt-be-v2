package com.filemngt.v2.mediaworker;

import com.filemngt.v2.mediaworker.config.MediaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MediaProperties.class)
public class MediaWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaWorkerApplication.class, args);
    }
}
