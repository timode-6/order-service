package com.example.order_service.dto.request.order_request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.util.List;

import com.example.order_service.dto.request.order_item_request.*;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
 
    @NotNull(message = "User ID must not be null")
    private Long userId;
 
    @NotNull(message = "Total price must not be null")
    @Positive(message = "Total price must be positive")
    private Long totalPrice;
 
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> orderItems;
}
 