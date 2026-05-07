package com.example.order_service.kafka;

import com.example.order_service.kafka.events.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Value("${kafka.topics.order-created:order.created}")
    private String topic;

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing ORDER_CREATED event: eventId={} orderId={} userId={} amount={}",
                event.getEventId(), event.getOrderId(),
                event.getUserId(), event.getTotalAmount());

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                kafkaTemplate.send(topic, event.getOrderId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish ORDER_CREATED event for orderId={}: {}",
                        event.getOrderId(), ex.getMessage(), ex);
            } else {
                log.debug("ORDER_CREATED event delivered → topic={} partition={} offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}