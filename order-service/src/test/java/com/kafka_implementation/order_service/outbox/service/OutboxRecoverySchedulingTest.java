package com.kafka_implementation.order_service.outbox.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class OutboxRecoverySchedulingTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                SchedulingTestConfiguration.class,
                OutboxRecoveryScheduler.class
            )
            .withPropertyValues(
          "app.outbox.recovery.enabled=true",
                "app.outbox.recovery.batch-size=5",
                "app.outbox.recovery.fixed-delay-ms=50"
            );

    @Test
    void shouldAutomaticallyTriggerRecoveryWhenSchedulingEnabled() {

        contextRunner.run(context -> {

            assertThat(context).hasNotFailed();

            OutboxRecoveryService recoveryService =
                    context.getBean(OutboxRecoveryService.class);

            verify(recoveryService, timeout(3000).atLeastOnce())
                .recoverExpiredBatch(
                    any(),
                    eq(5)
                );
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingTestConfiguration {

        @Bean
        OutboxRecoveryService recoveryService() {
            return mock(OutboxRecoveryService.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
