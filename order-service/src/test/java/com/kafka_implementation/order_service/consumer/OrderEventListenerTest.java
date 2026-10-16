package com.kafka_implementation.order_service.consumer;

import com.kafka_implementation.order_service.service.OrderEventProcessor;
import com.kafka_implementation.shared_events.base.EventMetadata;
import com.kafka_implementation.shared_events.payment.PaymentFailedEvent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = OrderEventListenerTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration"
        }
)
class OrderEventListenerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(OrderEventListener.class)
    static class TestApplication {
    }

    @Autowired
    private OrderEventListener listener;

    @MockBean
    private OrderEventProcessor processor;

    @Test
    void shouldPropagateProcessingFailureToKafka() {

        UUID orderId = UUID.randomUUID();

        PaymentFailedEvent event = new PaymentFailedEvent(
                EventMetadata.create("payment-service", 1),
                orderId,
                "Payment declined"
        );

        when(processor.processPaymentFailure(event))
                .thenThrow(new IllegalStateException(
                        "Simulated database failure"
                ));

        assertThatThrownBy(() -> listener.onPaymentFailed(event))
                .as("Processing failure must escape the listener")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated database failure");

        verify(processor, times(1))
                .processPaymentFailure(event);

        verifyNoMoreInteractions(processor);
    }
}
