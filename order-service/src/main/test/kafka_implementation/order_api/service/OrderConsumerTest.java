package kafka_implementation.order_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.repository.OrderRepository;
import com.kafka_implementation.shared.dto.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderConsumer orderConsumer;

    @BeforeEach
    void setUp() {
        orderConsumer = new OrderConsumer(orderRepository, objectMapper);
    }

    @Test
    void testHandleStockUpdate_ApprovedOrder() throws Exception {
        // Arrange
        String key = "order-key";
        String value = "{\"orderId\":123,\"productCode\":\"P123\",\"quantity\":10,\"approved\":true}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory-update-topic", 0, 0L, key, value);

        PaymentEvent event = new PaymentEvent(123L, "P123", 10, true);
        when(objectMapper.readValue(value, PaymentEvent.class)).thenReturn(event);

        // Act
        orderConsumer.handleStockUpdate(record);

        // Assert
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testHandleStockUpdate_RejectedOrder() throws Exception {
        // Arrange
        String key = "order-key";
        String value = "{\"orderId\":123,\"productCode\":\"P123\",\"quantity\":10,\"approved\":false}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory-update-topic", 0, 0L, key, value);

        PaymentEvent event = new PaymentEvent(123L, "P123", 10, false);
        when(objectMapper.readValue(value, PaymentEvent.class)).thenReturn(event);

        // Act
        orderConsumer.handleStockUpdate(record);

        // Assert
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testHandleStockUpdate_ExceptionHandling() throws Exception {
        // Arrange
        String key = "order-key";
        String value = "invalid-json";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory-update-topic", 0, 0L, key, value);

        when(objectMapper.readValue(value, PaymentEvent.class)).thenThrow(new RuntimeException("Deserialization error"));

        // Act
        orderConsumer.handleStockUpdate(record);

        // Assert
        verify(orderRepository, never()).save(any(Order.class));
    }
}
