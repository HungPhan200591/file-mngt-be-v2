package com.filemngt.v2.mediaworker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class MediaCatalogClientConfiguration {

    @Bean
    RestClient mediaCatalogRestClient(MediaProperties properties) {
        var catalog = properties.catalog();
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(catalog.connectTimeout());
        requestFactory.setReadTimeout(catalog.readTimeout());
        return RestClient.builder()
                .baseUrl(catalog.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
