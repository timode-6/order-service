package com.example.order_service.dto.response.order_response;
 
import com.example.order_service.model.*;
import com.example.order_service.dto.response.order_item_response.OrderItemResponse;
import com.example.order_service.dto.response.user_response.*;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;
 
import java.time.Instant;
import java.util.List;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private Long totalPrice;
    private List<OrderItemResponse> orderItems;
    private Instant createdAt;
    private Instant updatedAt;
 
    private UserResponse user;
}
 