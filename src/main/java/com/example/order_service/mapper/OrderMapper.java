
package com.example.order_service.mapper;

import com.example.order_service.dto.request.order_request.CreateOrderRequest;
import com.example.order_service.dto.request.order_request.UpdateOrderRequest;
import com.example.order_service.dto.response.order_response.OrderResponse;
import com.example.order_service.dto.response.user_response.UserResponse;
import com.example.order_service.model.Order;
import org.mapstruct.*;
 
@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {
 
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "status", expression = "java(com.example.order_service.model.OrderStatus.PENDING)")
    Order toEntity(CreateOrderRequest request);
 
    @Mapping(target = "user", ignore = true)
    OrderResponse toResponse(Order order);
 
    default OrderResponse toResponse(Order order, UserResponse user) {
        OrderResponse response = toResponse(order);response.setUser(user);
        return response;
    }
 
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    void updateEntityFromRequest(UpdateOrderRequest request, @MappingTarget Order order);
}