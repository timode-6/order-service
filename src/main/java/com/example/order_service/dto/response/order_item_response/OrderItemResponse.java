package com.example.order_service.dto.response.order_item_response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.time.Instant;

import com.example.order_service.dto.response.item_response.ItemResponse;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Integer quantity;
    private ItemResponse item;
    private Instant createdAt;
    private Instant updatedAt;
}
 