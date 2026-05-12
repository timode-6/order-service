package com.example.order_service.kafka;

import com.example.order_service.kafka.events.CreatePaymentEvent;
import com.example.order_service.model.Order;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.OrderRepository;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DirtiesContext
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class KafkaIntegrationTest {


    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("kafka.consumer.group-id",
                () -> "test-group-" + UUID.randomUUID());
    }


    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        ProducerFactory<String, CreatePaymentEvent> testProducerFactory(
                KafkaProperties props) {
            Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties());
            cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
            cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
            cfg.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(cfg);
        }

        @Bean
        KafkaTemplate<String, CreatePaymentEvent> paymentKafkaTemplate(
                ProducerFactory<String, CreatePaymentEvent> testProducerFactory) {
            return new KafkaTemplate<>(testProducerFactory);
        }
    }


    @Autowired
    private KafkaTemplate<String, CreatePaymentEvent> paymentKafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Value("${kafka.topics.payment-created:payment.created}")
    private String topic;

    private Order savedOrder;


    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }

        orderRepository.deleteAll();

        Order order = new Order();
        order.setUserId(1L);
        order.setUserEmail("test@example.com");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(5000L);
        savedOrder = orderRepository.save(order);
    }


    @Test
    void completedEvent_orderConfirmed() {
        paymentKafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                buildEvent(savedOrder.getId(), "SUCCESS"));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.SUCCESS);
                });
    }

    @Test
    void failedEvent_orderCancelled() {
        paymentKafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                buildEvent(savedOrder.getId(), "FAILED"));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                });
    }

    @Test
    void duplicateEvent_idempotent() {
        paymentKafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                buildEvent(savedOrder.getId(), "SUCCESS"));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.SUCCESS);
                });

        paymentKafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                buildEvent(savedOrder.getId(), "FAILED"));

        await().pollDelay(3, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.SUCCESS);
                });
    }


    private CreatePaymentEvent buildEvent(Long orderId, String paymentStatus) {
        return CreatePaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .paymentId("pay-" + UUID.randomUUID())
                .orderId(String.valueOf(orderId))
                .userId("user-1")
                .paymentStatus(paymentStatus)
                .paymentAmount(5000L)
                .timestamp(Instant.now())
                .build();
    }
}