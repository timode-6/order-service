package com.example.order_service.repository; 

import com.example.order_service.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import java.util.List;
import java.util.Optional;
 
@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
 
    Optional<Item> findByName(String name);
 
    List<Item> findByPriceBetween(Long min, Long max);
 
    @Query("SELECT i FROM Item i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Item> searchByName(@Param("name") String name);
}