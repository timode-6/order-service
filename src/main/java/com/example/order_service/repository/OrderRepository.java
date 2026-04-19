package com.example.order_service.repository;

import com.example.order_service.model.Order;
import com.example.order_service.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import java.util.List;
import java.util.Optional;
 
@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>{
 
    List<Order> findByUserId(Long userId);
 
    Page<Order> findByUserId(Long userId, Pageable pageable);
 
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status IN :statuses")
    List<Order> findByUserIdAndStatuses(
            @Param("userId") Long userId,
            @Param("statuses") List<OrderStatus> statuses
    );
 
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems oi JOIN FETCH oi.item WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);
 
    @Modifying
    @Query("UPDATE Order o SET o.deleted = true WHERE o.id = :id")
    void softDeleteById(@Param("id") Long id);
 
    @Query(value = "SELECT * FROM orders WHERE id = :id", nativeQuery = true)
    Optional<Order> findByIdIncludeDeleted(@Param("id") Long id);
}
 