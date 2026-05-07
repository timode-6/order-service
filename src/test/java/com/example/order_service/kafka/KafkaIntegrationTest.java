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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
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

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class KafkaIntegrationTest {

        @Container
        static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

        @Container
        static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                        .withDatabaseName("order_db")
                        .withUsername("order_user")
                        .withPassword("order_pass");

        @DynamicPropertySource
        static void overrideProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);

                registry.add("spring.kafka.producer.key-serializer",
                                () -> "org.apache.kafka.common.serialization.StringSerializer");
                registry.add("spring.kafka.producer.value-serializer",
                                () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        }

        @Autowired
        private KafkaTemplate<String, CreatePaymentEvent> kafkaTemplate;

        @Autowired
        private OrderRepository orderRepository;

        @Value("${kafka.topics.payment-created:payment.created}")
        private String topic;

        private Order savedOrder;

        @TestConfiguration
        static class KafkaManualConfig {
                @Bean
                public ProducerFactory<String, CreatePaymentEvent> producerFactory() {
                        Map<String, Object> config = new HashMap<>();
                        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
                        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
                        return new DefaultKafkaProducerFactory<>(config);
                }

                @Bean
                public KafkaTemplate<String, CreatePaymentEvent> kafkaTemplate(
                                ProducerFactory<String, CreatePaymentEvent> pf) {
                        return new KafkaTemplate<>(pf);
                }
        }

        @BeforeEach
        void setUp() {
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
                kafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                                buildEvent(savedOrder.getId(), "SUCCESS"));

                await().atMost(5, TimeUnit.SECONDS)
                                .untilAsserted(() -> {
                                        Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                                        assertThat(updated.getStatus()).isEqualTo(OrderStatus.SUCCESS);
                                });
        }

        @Test
        void failedEvent_orderCancelled() {
                kafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                                buildEvent(savedOrder.getId(), "FAILED"));

                await().atMost(5, TimeUnit.SECONDS)
                                .untilAsserted(() -> {
                                        Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                                        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                                });
        }

        @Test
        void duplicateEvent_idempotent() {
                kafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                                buildEvent(savedOrder.getId(), "SUCCESS"));

                await().atMost(5, TimeUnit.SECONDS)
                                .untilAsserted(() -> {
                                        Order updated = orderRepository.findById(savedOrder.getId()).orElseThrow();
                                        assertThat(updated.getStatus()).isEqualTo(OrderStatus.SUCCESS);
                                });

                kafkaTemplate.send(topic, String.valueOf(savedOrder.getId()),
                                buildEvent(savedOrder.getId(), "FAILED"));

                await().during(2, TimeUnit.SECONDS)
                                .atMost(4, TimeUnit.SECONDS)
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