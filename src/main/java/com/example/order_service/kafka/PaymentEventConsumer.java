package com.example.order_service.kafka;

import com.example.order_service.model.Order;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.kafka.events.CreatePaymentEvent;
import com.example.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final Set<OrderStatus> ACTIONABLE_STATUSES = Set.of(OrderStatus.PENDING);

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "${kafka.topics.payment-created:payment.created}", groupId = "${kafka.consumer.group-id:order-service-group}", containerFactory = "paymentEventListenerContainerFactory")
    @Transactional
    public void handlePaymentCreated(
            @Payload CreatePaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Received CREATE_PAYMENT event: eventId={} paymentId={} orderId={} status={} " +
                "[topic={} partition={} offset={}]",
                event.getEventId(), event.getPaymentId(),
                event.getOrderId(), event.getPaymentStatus(),
                topic, partition, offset);

        try {
            processEvent(event);
            ack.acknowledge();
            log.debug("Acknowledged event eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("Error processing CREATE_PAYMENT event eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private void processEvent(CreatePaymentEvent event) {
        Long orderId = parseOrderId(event.getOrderId());

        if (orderId == null) {
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found for paymentId={}; skipping event",
                    orderId, event.getPaymentId());
            return;
        }

        if (!ACTIONABLE_STATUSES.contains(order.getStatus())) {
            log.info("Order {} is already in status={}; skipping duplicate/late event for paymentId={}",
                    orderId, order.getStatus(), event.getPaymentId());
            return;
        }

        OrderStatus newStatus = resolveOrderStatus(event.getPaymentStatus());
        if (newStatus == null) {
            log.warn("Unrecognised paymentStatus='{}' in event eventId={}; skipping",
                    event.getPaymentStatus(), event.getEventId());
            return;
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        log.info("Order {} transitioned {} → {} (paymentId={}, paymentStatus={})",
                orderId,
                OrderStatus.PENDING,
                newStatus,
                event.getPaymentId(),
                event.getPaymentStatus());
    }

    private static OrderStatus resolveOrderStatus(String paymentStatus) {
        if (paymentStatus == null)
            return null;
        return switch (paymentStatus.toUpperCase()) {
            case "SUCCESS" -> OrderStatus.SUCCESS;
            case "FAILED" -> OrderStatus.CANCELLED;
            default -> null;
        };
    }

    private Long parseOrderId(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("Received event with null/blank orderId — skipping");
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            log.warn("Received event with non-numeric orderId '{}' — skipping", raw);
            return null;
        }
    }
}