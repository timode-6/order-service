package com.example.order_service.service.order;

import com.example.order_service.dto.request.order_item_request.OrderItemRequest;
import com.example.order_service.dto.request.order_request.CreateOrderRequest;
import com.example.order_service.dto.request.order_request.OrderFilterRequest;
import com.example.order_service.dto.request.order_request.UpdateOrderRequest;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.OrderResponse;
import com.example.order_service.dto.response.user_response.UserResponse;
import com.example.order_service.model.Item;
import com.example.order_service.model.Order;
import com.example.order_service.model.OrderItem;
import com.example.order_service.exception.ItemNotFoundException;
import com.example.order_service.exception.OrderNotFoundException;
import com.example.order_service.exception.UserServiceUnavailableException;
import com.example.order_service.mapper.OrderMapper;
import com.example.order_service.repository.ItemRepository;
import com.example.order_service.repository.OrderRepository;
import com.example.order_service.service.client.UserServiceClient;
import com.example.order_service.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for userId={}", request.getUserId());

        Order order = orderMapper.toEntity(request);

        List<OrderItem> items = buildOrderItems(request.getOrderItems(), order);
        items.forEach(order::addOrderItem);

        Order saved = orderRepository.save(order);
        log.info("Order created with id={}", saved.getId());

        UserResponse user = fetchUser(saved.getUserEmail());
        return orderMapper.toResponse(saved, user);
    }


    @Override
    public OrderResponse getOrderById(Long id) {
        log.debug("Fetching order id={}", id);
        Order order = findActiveOrder(id);
        UserResponse user = fetchUser(order.getUserEmail());
        return orderMapper.toResponse(order, user);
    }


    @Override
    public PageResponse<OrderResponse> getOrdersFiltered(OrderFilterRequest filter) {
        log.debug("Fetching orders with filter: {}", filter);

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(filter.getSortDir())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                filter.getSortBy()
        );
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<Order> spec = OrderSpecification.withFilters(
                filter.getCreatedFrom(),
                filter.getCreatedTo(),
                filter.getStatuses()
        );

        Page<Order> page = orderRepository.findAll(spec, pageable);

        List<OrderResponse> responses = page.getContent().stream()
                .map(o -> {
                    UserResponse user = fetchUser(o.getUserEmail());
                    return orderMapper.toResponse(o, user);
                })
                .toList();

        return PageResponse.<OrderResponse>builder()
                .content(responses)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }


    @Override
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        log.debug("Fetching orders for userId={}", userId);
 
        List<Order> orders = orderRepository.findByUserId(userId);
        if (orders.isEmpty()) {
            return List.of();
        }
 
        UserResponse user = fetchUser(orders.get(0).getUserEmail());
 
        return orders.stream()
                .map(o -> orderMapper.toResponse(o, user))
                .toList();
    }
 


    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request) {
        log.info("Updating order id={}", id);
        Order order = findActiveOrder(id);

        orderMapper.updateEntityFromRequest(request, order);

        if (request.getOrderItems() != null && !request.getOrderItems().isEmpty()) {
            order.getOrderItems().clear();   
            List<OrderItem> newItems = buildOrderItems(request.getOrderItems(), order);
            newItems.forEach(order::addOrderItem);
        }

        Order updated = orderRepository.save(order);
        log.info("Order id={} updated", id);

        UserResponse user = fetchUser(updated.getUserEmail());
        return orderMapper.toResponse(updated, user);
    }


    @Override
    @Transactional
    public void deleteOrder(Long id) {
        log.info("Soft-deleting order id={}", id);
        Order order = findActiveOrder(id);
        orderRepository.delete(order);
        log.info("Order id={} soft-deleted", id);
    }

    private UserResponse fetchUser(String email) {
        try {
            return userServiceClient.getUserByEmail(email);
        } catch (UserServiceUnavailableException ex) {
             log.warn("User Service unavailable for email='{}' — returning order without user info: {}",
                email, ex.getMessage());
            return null; 
        }
    }


    private Order findActiveOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private List<OrderItem> buildOrderItems(List<OrderItemRequest> itemRequests, Order order) {
        List<OrderItem> result = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            Item item = itemRepository.findById(req.getItemId())
                    .orElseThrow(() -> new ItemNotFoundException(req.getItemId()));
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setItem(item);
            oi.setQuantity(req.getQuantity());
            result.add(oi);
        }
        return result;
    }
}