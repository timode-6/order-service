package com.example.order_service.mapper;
 
import com.example.order_service.dto.response.item_response.ItemResponse;
import com.example.order_service.model.Item;

import org.mapstruct.Mapper;
 
@Mapper(componentModel = "spring")
public interface ItemMapper {
 
    ItemResponse toResponse(Item item);
}
 
