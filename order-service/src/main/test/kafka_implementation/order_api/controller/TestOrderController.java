package kafka_implementation.order_api.controller;

import com.kafka_implementation.order_service.OrderApiApplication;
import com.kafka_implementation.order_service.domain.Order;
import com.kafka_implementation.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderApiApplication.class)
@Testcontainers
@AutoConfigureMockMvc
public class TestOrderController {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    public void setUp() {
        orderRepository.deleteAll(); // Clean up the database before each test
    }

    @Test
    public void testListAllOrders() throws Exception {
        // Prepopulate the database with some realistic orders
        Order order1 = new Order(101L, "SPH-128", 1, "PENDING");
        Order order2 = new Order(102L, "LTP-GPU", 1, "COMPLETED");
        Order order3 = new Order(103L, "CH-ERG", 1, "CANCELLED");


        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);

        // Perform the GET request and verify the result
        mockMvc.perform(MockMvcRequestBuilders.get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("order-list"))
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("orders", hasSize(3)));
    }

    @Test
    public void testCreateOrder() throws Exception {
        // Perform the POST request to create an order
        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .param("productCode", "SPH-128")
                        .param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("order-confirmation"))
                .andExpect(model().attributeExists("message"))
                .andExpect(model().attributeExists("order"));

        // Verify the order is saved in the database
        Order savedOrder = orderRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(savedOrder).isNotNull();
        org.assertj.core.api.Assertions.assertThat(savedOrder.getProductCode()).isEqualTo("SPH-128");
        org.assertj.core.api.Assertions.assertThat(savedOrder.getQuantity()).isEqualTo(2);
    }
}
