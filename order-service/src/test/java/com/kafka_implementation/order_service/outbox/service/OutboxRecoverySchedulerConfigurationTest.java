package com.kafka_implementation.order_service.outbox.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxRecoverySchedulerConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            TestConfiguration.class,
                            OutboxRecoveryScheduler.class
                    );

    @Test
    void shouldNotRegisterSchedulerWhenDisabled() {

        contextRunner
                .withPropertyValues(
                        "app.outbox.recovery.enabled=false"
                )
                .run(context -> {

                    assertThat(context)
                            .doesNotHaveBean(
                                    OutboxRecoveryScheduler.class
                            );
                });
    }

    @Test
    void shouldRegisterSchedulerWhenEnabled() {

        contextRunner
                .withPropertyValues(
                        "app.outbox.recovery.enabled=true",
                        "app.outbox.recovery.batch-size=100"
                )
                .run(context -> {

                    assertThat(context)
                            .hasSingleBean(
                                    OutboxRecoveryScheduler.class
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

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
