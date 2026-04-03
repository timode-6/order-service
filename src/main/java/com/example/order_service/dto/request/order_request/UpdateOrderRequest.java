package com.example.order_service.dto.request.order_request;

import com.example.order_service.dto.request.order_item_request.OrderItemRequest;
import com.example.order_service.model.OrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.util.List;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {
 
    @NotNull(message = "Status must not be null")
    private OrderStatus status;
 
    @NotNull(message = "Total price must not be null")
    @Positive(message = "Total price must be positive")
    private Long totalPrice;
 
    @Valid
    private List<OrderItemRequest> orderItems;
}
 