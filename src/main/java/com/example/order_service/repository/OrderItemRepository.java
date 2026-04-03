package com.example.order_service.repository;

import com.example.order_service.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional; 
import java.util.List;
 
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
 
    List<OrderItem> findByOrderId(Long orderId);
 
    
    List<OrderItem> findByItemId(Long itemId);
    
    Optional<OrderItem> findByOrderIdAndItemId(Long orderId, Long itemId);
    
    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.item.id = :itemId")
    long countOrdersContainingItem(@Param("itemId") Long itemId);

    void deleteByOrderId(Long orderId);
}
 