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

    private static final Logger log = LoggerFactory.getLogger(CorrelationLoggingFilter.class);

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACE_ID = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String correlationId = getOrCreate(exchange, CORRELATION_ID);
        String traceId = getOrCreate(exchange, TRACE_ID);
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "UNKNOWN";

        long start = System.currentTimeMillis();

        MDC.put("correlationId", correlationId);
        MDC.put("traceId", traceId);
        MDC.put("httpMethod", method);
        MDC.put("httpPath", exchange.getRequest().getURI().getPath());

        ServerHttpRequest mutatedRequest =
                exchange.getRequest()
                        .mutate()
                        .header(CORRELATION_ID, correlationId)
                        .header(TRACE_ID, traceId)
                        .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doOnSuccess(v -> logResponse(exchange, start))
                .doOnError(ex -> logError(exchange, start, ex))
                .doFinally(signal -> MDC.clear());
    }

    private String getOrCreate(ServerWebExchange exchange, String header) {
        return exchange.getRequest()
                .getHeaders()
                .getFirst(header) != null
                ? exchange.getRequest().getHeaders().getFirst(header)
                : UUID.randomUUID().toString();
    }

    private void logResponse(ServerWebExchange exchange, long start) {
        MDC.put("status", String.valueOf(exchange.getResponse().getStatusCode()));
        MDC.put("latencyMs", String.valueOf(System.currentTimeMillis() - start));

        log.info("Gateway request completed");
    }

    private void logError(ServerWebExchange exchange, long start, Throwable ex) {
        MDC.put("status", "500");
        MDC.put("latencyMs", String.valueOf(System.currentTimeMillis() - start));

        log.error("Gateway request failed", ex);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
