package com.example.order_service.mapper;

import com.example.order_service.model.OrderItem;
import com.example.order_service.dto.request.order_item_request.*;
import com.example.order_service.dto.response.order_item_response.OrderItemResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
 
@Mapper(componentModel = "spring", uses = {ItemMapper.class})
public interface OrderItemMapper {
 
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "item", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OrderItem toEntity(OrderItemRequest request);
 
    OrderItemResponse toResponse(OrderItem orderItem);
}
 