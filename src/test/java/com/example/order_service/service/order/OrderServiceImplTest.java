package com.example.order_service.service.order;

import com.example.order_service.dto.request.order_item_request.OrderItemRequest;
import com.example.order_service.dto.request.order_request.*;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.*;
import com.example.order_service.dto.response.user_response.*;
import com.example.order_service.model.*;
import com.example.order_service.exception.ItemNotFoundException;
import com.example.order_service.exception.OrderNotFoundException;
import com.example.order_service.mapper.OrderMapper;
import com.example.order_service.repository.ItemRepository;
import com.example.order_service.repository.OrderRepository;
import com.example.order_service.service.client.UserServiceClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;


    private Item item;
    private Order order;
    private OrderResponse orderResponse;
    private UserResponse user;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setId(1L);
        item.setName("Widget");
        item.setPrice(999L);

        order = new Order(10L, 5L, OrderStatus.PENDING, 1998L, false, new ArrayList<>());

        user = UserResponse.builder()
                .id(5L)
                .name("Alice")
                .surname("Rossi")
                .email("alice.rossi@example.com")
                .build();

        orderResponse = OrderResponse.builder()
                .id(10L)
                .userId(5L)
                .status(OrderStatus.PENDING)
                .totalPrice(1998L)
                .user(user)
                .build();
    }


    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("creates order, persists cascade items, enriches with user")
        void shouldCreateOrderSuccessfully() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L)
                    .totalPrice(1998L)
                    .orderItems(List.of(new OrderItemRequest(1L, 2)))
                    .build();

            when(orderMapper.toEntity(req)).thenReturn(order);
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(orderRepository.save(order)).thenReturn(order);
            when(userServiceClient.getUserById(5L)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);

            OrderResponse result = orderService.createOrder(req);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getUser().getEmail()).isEqualTo("alice.rossi@example.com");
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("throws ItemNotFoundException when item does not exist")
        void shouldThrowWhenItemMissing() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L)
                    .totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(99L, 1)))
                    .build();

            when(orderMapper.toEntity(req)).thenReturn(order);
            when(itemRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(req))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining("99");

            verify(orderRepository, never()).save(any());
        }
    }


    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {

        @Test
        @DisplayName("returns enriched order when found")
        void shouldReturnOrderById() {
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(userServiceClient.getUserById(5L)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);

            OrderResponse result = orderService.getOrderById(10L);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getUser()).isNotNull();
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("returns order with null user when circuit breaker returns null")
        void shouldReturnOrderWithNullUserOnCircuitBreaker() {
            OrderResponse responseWithNullUser = OrderResponse.builder()
                    .id(10L).userId(5L).user(null).build();

            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(userServiceClient.getUserById(5L)).thenReturn(null);
            when(orderMapper.toResponse(order, (UserResponse) null)).thenReturn(responseWithNullUser);

            OrderResponse result = orderService.getOrderById(10L);
            assertThat(result.getUser()).isNull();
        }
    }

    @Nested
    @DisplayName("getOrdersFiltered")
    class GetOrdersFiltered {

        @Test
        @DisplayName("returns paginated results with user enrichment")
        void shouldReturnPagedOrders() {
            OrderFilterRequest filter = OrderFilterRequest.builder()
                    .page(0).size(10).sortBy("createdAt").sortDir("desc")
                    .statuses(List.of(OrderStatus.PENDING))
                    .build();

            var pageResult = new PageImpl<>(List.of(order));
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(pageResult);
            when(userServiceClient.getUserById(5L)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);

            PageResponse<OrderResponse> result = orderService.getOrdersFiltered(filter);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns empty page when no orders match")
        void shouldReturnEmptyPage() {
            OrderFilterRequest filter = OrderFilterRequest.builder()
                    .page(0).size(10).sortBy("createdAt").sortDir("asc").build();

            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            PageResponse<OrderResponse> result = orderService.getOrdersFiltered(filter);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }


    @Nested
    @DisplayName("getOrdersByUserId")
    class GetOrdersByUserId {

        @Test
        @DisplayName("returns all orders for a given user")
        void shouldReturnOrdersForUser() {
            when(orderRepository.findByUserId(5L)).thenReturn(List.of(order));
            when(userServiceClient.getUserById(5L)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);

            List<OrderResponse> result = orderService.getOrdersByUserId(5L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("returns empty list when user has no orders")
        void shouldReturnEmptyListWhenNoOrders() {
            when(orderRepository.findByUserId(5L)).thenReturn(List.of());
            when(userServiceClient.getUserById(5L)).thenReturn(user);

            List<OrderResponse> result = orderService.getOrdersByUserId(5L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateOrder")
    class UpdateOrder {

        @Test
        @DisplayName("updates order fields and replaces items when provided")
        void shouldUpdateOrder() {
            UpdateOrderRequest req = UpdateOrderRequest.builder()
                    .status(OrderStatus.CONFIRMED)
                    .totalPrice(2999L)
                    .orderItems(List.of(new OrderItemRequest(1L, 3)))
                    .build();

            OrderResponse updatedResponse = OrderResponse.builder()
                    .id(10L).status(OrderStatus.CONFIRMED)
                    .totalPrice(2999L).user(user).build();

            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            doNothing().when(orderMapper).updateEntityFromRequest(req, order);
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(orderRepository.save(order)).thenReturn(order);
            when(userServiceClient.getUserById(5L)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(updatedResponse);

            OrderResponse result = orderService.updateOrder(10L, req);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderMapper).updateEntityFromRequest(req, order);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());
            
            UpdateOrderRequest request = UpdateOrderRequest.builder()
                .status(OrderStatus.CANCELLED)
                .totalPrice(0L)
                .build();

            assertThatThrownBy(() -> orderService.updateOrder(999L, request))
                .isInstanceOf(OrderNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }
    }


    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrder {

        @Test
        @DisplayName("soft-deletes existing order")
        void shouldSoftDeleteOrder() {
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            doNothing().when(orderRepository).delete(order);

            assertThatCode(() -> orderService.deleteOrder(10L)).doesNotThrowAnyException();

            verify(orderRepository).delete(order);
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.deleteOrder(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("999");

            verify(orderRepository, never()).delete(order);
        }
    }
}