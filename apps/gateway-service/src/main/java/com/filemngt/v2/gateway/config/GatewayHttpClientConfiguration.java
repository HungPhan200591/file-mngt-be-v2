package com.filemngt.v2.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(GatewayHttpClientProperties.class)
class GatewayHttpClientConfiguration {

    @Bean
    ClientHttpRequestFactory gatewayClientHttpRequestFactory(GatewayHttpClientProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
