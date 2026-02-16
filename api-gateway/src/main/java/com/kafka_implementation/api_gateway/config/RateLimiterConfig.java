package com.kafka_implementation.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange ->
                Mono.just(
                        exchange.getRequest()
                                .getRemoteAddress()
                                .getAddress()
                                .getHostAddress()
                );
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange ->
                Mono.just(
                        exchange.getRequest()
                                .getHeaders()
                                .getFirst("X-User-Id")
                );
    }
}
