package com.example.order_service.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private String eventId;
    private Long orderId;
    private Long userId;
    private String userEmail;
    private Long totalAmount;
    private Instant timestamp;
}