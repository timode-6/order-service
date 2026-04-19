package com.example.order_service.dto.request.order_request;
 
import com.example.order_service.model.OrderStatus;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
 
import java.time.Instant;
import java.util.List;
 
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFilterRequest {
 
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant createdFrom;
 
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant createdTo;
 
    private List<OrderStatus> statuses;
 
    @Min(value = 0, message = "Page must be >= 0")
    @Builder.Default
    private int page = 0;
 
    @Min(value = 1, message = "Size must be >= 1")
    @Builder.Default
    private int size = 20;
 
    @Builder.Default
    private String sortBy = "createdAt";
 
    @Builder.Default
    private String sortDir = "desc";
}