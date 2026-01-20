package com.kafka_implementation.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(CorrelationLoggingFilter.class);

    public static final String CORRELATION_ID = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        String method = request.getMethod() != null
                ? request.getMethod().name()
                : "UNKNOWN";

        long start = System.currentTimeMillis();

        String finalCorrelationId = correlationId;

        ServerHttpRequest mutatedRequest =
                request.mutate()
                        .header(CORRELATION_ID, finalCorrelationId)
                        .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doOnSubscribe(sub -> {
                    MDC.put("correlationId", finalCorrelationId);
                    MDC.put("httpMethod", method);
                    MDC.put("path", request.getURI().getPath());
                })
                .doOnSuccess(v -> logResponse(exchange, start))
                .doOnError(ex -> logError(exchange, start, ex))
                .doFinally(signal -> MDC.clear());
    }

    private void logResponse(ServerWebExchange exchange, long start) {
        MDC.put("status",
                exchange.getResponse().getStatusCode() != null
                        ? exchange.getResponse().getStatusCode().toString()
                        : "UNKNOWN");
        MDC.put("latencyMs",
                String.valueOf(System.currentTimeMillis() - start));

        log.info("Gateway request completed");
    }

    private void logError(ServerWebExchange exchange, long start, Throwable ex) {
        MDC.put("status", "500");
        MDC.put("latencyMs",
                String.valueOf(System.currentTimeMillis() - start));

        log.error("Gateway request failed", ex);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

