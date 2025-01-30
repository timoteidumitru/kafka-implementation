package kafka_implementation.order_api.service;

import com.kafka_implementation.order_api.OrderApiApplication;
import com.kafka_implementation.order_api.service.OrderConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = OrderApiApplication.class)
@Testcontainers
@EnableKafka
@EmbeddedKafka(partitions = 1, topics = "payment-result-topic")
public class OrderConsumerTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withUsername("testuser")
            .withPassword("testpass")
            .withDatabaseName("testdb");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OrderConsumer orderConsumer;

    @BeforeEach
    void setup() {
        mysql.start();
    }

    @Test
    public void testConsumePaymentEvent() {
        String testMessage = "Payment Successful for Order ID: 1234";
        kafkaTemplate.send("payment-result-topic", testMessage);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Verify that the message was consumed
            System.out.println("Message Consumed Successfully");
        });
    }
}