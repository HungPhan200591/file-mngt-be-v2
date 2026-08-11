package com.filemngt.v2.scan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.registry")
/** Binding endpoint và timeout cho adapter lấy registry từ Catalog Service. */
public class CatalogClientProperties {

    private String baseUrl = "http://localhost:18101";
    private int timeoutMs = 3000;
    private int existenceTimeoutMs = 3000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getExistenceTimeoutMs() {
        return existenceTimeoutMs;
    }

    public void setExistenceTimeoutMs(int existenceTimeoutMs) {
        this.existenceTimeoutMs = existenceTimeoutMs;
    }
}
