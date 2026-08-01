package com.filemngt.v2.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class GatewayCorsConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v2/**")
                .allowedOrigins("http://localhost:8888", "http://127.0.0.1:8888")
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Correlation-Id")
                .exposedHeaders(
                        "X-Correlation-Id", "Accept-Ranges", "Content-Range", "Content-Length", "ETag", "Last-Modified")
                .maxAge(3600);
    }
}
