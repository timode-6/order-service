package com.example.order_service.dto.response.order_response;
 
import com.example.order_service.model.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.example.order_service.dto.response.order_item_response.OrderItemResponse;
import com.example.order_service.dto.response.user_response.*;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
 
@Data
@Builder
@Setter
@Jacksonized 
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private Long totalPrice;
    private List<OrderItemResponse> orderItems;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant updatedAt;
 
    private UserResponse user;
}
 