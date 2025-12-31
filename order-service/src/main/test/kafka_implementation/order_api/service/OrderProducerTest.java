package kafka_implementation.order_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.shared.dto.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderProducer orderProducer;

    @BeforeEach
    void setUp() {
        orderProducer = new OrderProducer(kafkaTemplate, objectMapper);
    }

    @Test
    void testSendOrderEvent_Success() throws Exception {
        // Arrange
        String productCode = "P123";
        int quantity = 10;
        long orderId = 500L;
        OrderEvent orderEvent = new OrderEvent(orderId, productCode, quantity, "buy");
        String message = "{\"orderId\":500,\"productCode\":\"P123\",\"quantity\":10,\"operation\":\"buy\"}";

        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenReturn(message);

        // Act
        orderProducer.sendOrderEvent(productCode, quantity);

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("order.topic"), eq(message));
    }

    @Test
    void testSendOrderEvent_ExceptionHandling() throws Exception {
        // Arrange
        String productCode = "P123";
        int quantity = 10;

        when(objectMapper.writeValueAsString(any(OrderEvent.class))).thenThrow(new RuntimeException("Serialization error"));

        // Act
        orderProducer.sendOrderEvent(productCode, quantity);

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }
}

