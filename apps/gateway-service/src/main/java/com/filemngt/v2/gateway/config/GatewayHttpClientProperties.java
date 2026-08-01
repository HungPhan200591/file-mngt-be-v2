package com.filemngt.v2.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.http-client")
public record GatewayHttpClientProperties(Duration connectTimeout, Duration readTimeout) {}
