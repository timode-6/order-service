package com.example.order_service.dto.response.item_response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.time.Instant;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponse {
    private Long id;
    private String name;
    private Long price;
    private Instant createdAt;
    private Instant updatedAt;
}
 