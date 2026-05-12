package com.example.order_service.kafka;

import com.example.order_service.model.Order;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.OrderRepository;
import com.example.order_service.kafka.events.CreatePaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private PaymentEventConsumer consumer;

    private static final Long ORDER_ID = 42L;
    private static final String PAYMENT_ID = "payment_id";

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = new Order();
        pendingOrder.setId(ORDER_ID);
        pendingOrder.setUserId(1L);
        pendingOrder.setUserEmail("user@example.com");
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setTotalPrice(1000L);
    }

    private CreatePaymentEvent event(String status) {
        return CreatePaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .paymentId(PAYMENT_ID)
                .orderId(String.valueOf(ORDER_ID))
                .userId("user-1")
                .paymentStatus(status)
                .paymentAmount(1000L)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void pendingOrder_becomesConfirmed() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.handlePaymentCreated(event("SUCCESS"), "payment.created", 0, 0L, ack);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.SUCCESS);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("PENDING order → transitions to CANCELLED and acknowledges")
    void pendingOrder_becomesCancelled() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.handlePaymentCreated(event("FAILED"), "payment.created", 0, 0L, ack);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(ack).acknowledge();
    }

    @Test
    void alreadyConfirmed_skipped() {
        pendingOrder.setStatus(OrderStatus.SUCCESS);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        consumer.handlePaymentCreated(event("COMPLETED"), "payment.created", 0, 0L, ack);

        verify(orderRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void alreadyCancelled_skipped() {
        pendingOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        consumer.handlePaymentCreated(event("FAILED"), "payment.created", 0, 0L, ack);

        verify(orderRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void alreadyShipped_skipped() {
        pendingOrder.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        consumer.handlePaymentCreated(event("COMPLETED"), "payment.created", 0, 0L, ack);

        verify(orderRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void orderNotFound_skipped() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        consumer.handlePaymentCreated(event("COMPLETED"), "payment.created", 0, 0L, ack);

        verify(orderRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void unknownStatus_skipped() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        consumer.handlePaymentCreated(event("REFUNDED"), "payment.created", 0, 0L, ack);

        verify(orderRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void invalidOrderId_isSkipped() {
        CreatePaymentEvent badEvent = event("COMPLETED");
        badEvent.setOrderId("not-a-number");

        consumer.handlePaymentCreated(badEvent, "payment.created", 0, 0L, ack);

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void repositoryThrows_noAck() {
        when(orderRepository.findById(ORDER_ID))
                .thenThrow(new RuntimeException("DB connection lost"));
        CreatePaymentEvent event = event("COMPLETED");

        assertThatThrownBy(() -> consumer.handlePaymentCreated(event, "payment.created", 0, 0L, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection lost");
        verify(ack, never()).acknowledge();
    }
}