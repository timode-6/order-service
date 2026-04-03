package com.example.order_service.service.order;

import com.example.order_service.dto.request.order_request.CreateOrderRequest;
import com.example.order_service.dto.request.order_request.OrderFilterRequest;
import com.example.order_service.dto.request.order_request.UpdateOrderRequest;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.OrderResponse;

import java.util.List; 

public interface OrderService {
    
    OrderResponse getOrderById(Long id);

    OrderResponse createOrder(CreateOrderRequest request);
 
    PageResponse<OrderResponse> getOrdersFiltered(OrderFilterRequest filter);

    List<OrderResponse> getOrdersByUserId(Long userId);

    OrderResponse updateOrder(Long id, UpdateOrderRequest request); 

    void deleteOrder(Long id);
 
}
