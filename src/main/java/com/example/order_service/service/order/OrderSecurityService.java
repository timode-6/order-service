package com.example.order_service.service.order;

import org.springframework.stereotype.Service;
import com.example.order_service.dto.response.order_response.OrderResponse;
import lombok.RequiredArgsConstructor;

@Service("orderSecurity")
@RequiredArgsConstructor
public class OrderSecurityService {
    
    private final OrderService orderService;

    public boolean isOwner(Long orderId, String principalId) {
        OrderResponse order = orderService.getOrderById(orderId);
        return order.getUserId().toString().equals(principalId);
    }
}