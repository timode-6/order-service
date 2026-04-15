package com.example.order_service.service.order;

import com.example.order_service.dto.request.order_item_request.OrderItemRequest;
import com.example.order_service.dto.request.order_request.*;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.*;
import com.example.order_service.dto.response.user_response.*;
import com.example.order_service.model.*;
import com.example.order_service.exception.ItemNotFoundException;
import com.example.order_service.exception.OrderNotFoundException;
import com.example.order_service.exception.UserNotFoundException;
import com.example.order_service.exception.UserServiceUnavailableException;
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

    private static final String USER_EMAIL = "alice.rossi@example.com";

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

        order = new Order(10L, 5L, USER_EMAIL, OrderStatus.PENDING, 1998L, false, new ArrayList<>());

        user = UserResponse.builder()
                .id(5L)
                .name("Alice")
                .surname("Rossi")
                .email(USER_EMAIL)
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
        @DisplayName("creates order and enriches response with user fetched by email")
        void shouldCreateOrderSuccessfully() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L).userEmail(USER_EMAIL)
                    .totalPrice(1998L)
                    .orderItems(List.of(new OrderItemRequest(1L, 2)))
                    .build();
 
            when(orderMapper.toEntity(req)).thenReturn(order);
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(orderRepository.save(order)).thenReturn(order);
            when(userServiceClient.getUserByEmail(USER_EMAIL)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);
 
            OrderResponse result = orderService.createOrder(req);
 
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getUser().getEmail()).isEqualTo(USER_EMAIL);
            verify(userServiceClient).getUserByEmail(USER_EMAIL);
        }
 
        @Test
        @DisplayName("returns order with null user when User Service is unavailable (graceful degradation)")
        void degradesGracefullyOnUnavailable() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L).userEmail(USER_EMAIL)
                    .totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(1L, 1)))
                    .build();
 
            OrderResponse responseWithNullUser = OrderResponse.builder()
                    .id(10L).userId(5L).user(null).build();
 
            when(orderMapper.toEntity(req)).thenReturn(order);
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(orderRepository.save(order)).thenReturn(order);
            when(userServiceClient.getUserByEmail(USER_EMAIL))
                    .thenThrow(new UserServiceUnavailableException("circuit open"));
            when(orderMapper.toResponse(order, (UserResponse) null)).thenReturn(responseWithNullUser);
 
            OrderResponse result = orderService.createOrder(req);
 
            assertThat(result).isNotNull();
            assertThat(result.getUser()).isNull();
            verify(orderRepository).save(order);
        }
 
        @Test
        @DisplayName("propagates UserNotFoundException — domain error is not swallowed")
        void propagatesUserNotFound() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L).userEmail("ghost@example.com")
                    .totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(1L, 1)))
                    .build();
 
            Order ghostOrder = new Order();
            ghostOrder.setId(11L);
            ghostOrder.setUserId(5L);
            ghostOrder.setUserEmail("ghost@example.com");
            ghostOrder.setStatus(OrderStatus.PENDING);
            ghostOrder.setTotalPrice(10L);
 
            when(orderMapper.toEntity(req)).thenReturn(ghostOrder);
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(orderRepository.save(ghostOrder)).thenReturn(ghostOrder);
            when(userServiceClient.getUserByEmail("ghost@example.com"))
                    .thenThrow(new UserNotFoundException("ghost@example.com"));
 
            assertThatThrownBy(() -> orderService.createOrder(req))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("ghost@example.com");
        }
 
        @Test
        @DisplayName("throws ItemNotFoundException when item does not exist")
        void shouldThrowWhenItemMissing() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L).userEmail(USER_EMAIL).totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(99L, 1)))
                    .build();
 
            when(orderMapper.toEntity(req)).thenReturn(order);
            when(itemRepository.findById(99L)).thenReturn(Optional.empty());
 
            assertThatThrownBy(() -> orderService.createOrder(req))
                    .isInstanceOf(ItemNotFoundException.class)
                    .hasMessageContaining("99");
 
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(userServiceClient);
        }
    }


    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {
 
        @Test
        @DisplayName("returns enriched order when found")
        void shouldReturnOrderById() {
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(userServiceClient.getUserByEmail(USER_EMAIL)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);
 
            OrderResponse result = orderService.getOrderById(10L);
 
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getUser()).isNotNull();
        }
 
        @Test
        @DisplayName("returns order with null user when User Service is unavailable")
        void degradesGracefullyOnUnavailable() {
            OrderResponse responseWithNullUser = OrderResponse.builder()
                    .id(10L).userId(5L).user(null).build();
 
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(userServiceClient.getUserByEmail(USER_EMAIL))
                    .thenThrow(new UserServiceUnavailableException("circuit open"));
            when(orderMapper.toResponse(order, (UserResponse) null)).thenReturn(responseWithNullUser);
 
            OrderResponse result = orderService.getOrderById(10L);
            assertThat(result.getUser()).isNull();
        }
 
        @Test
        @DisplayName("propagates UserNotFoundException")
        void propagatesUserNotFound() {
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            when(userServiceClient.getUserByEmail(USER_EMAIL))
                    .thenThrow(new UserNotFoundException(USER_EMAIL));
 
            assertThatThrownBy(() -> orderService.getOrderById(10L))
                    .isInstanceOf(UserNotFoundException.class);
        }
 
        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());
 
            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("999");
 
            verifyNoInteractions(userServiceClient);
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
                    .statuses(List.of(OrderStatus.PENDING)).build();
 
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(order)));
            when(userServiceClient.getUserByEmail(USER_EMAIL)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);
 
            PageResponse<OrderResponse> result = orderService.getOrdersFiltered(filter);
 
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
 
        @Test
        @DisplayName("returns empty page without touching user service")
        void shouldReturnEmptyPage() {
            OrderFilterRequest filter = OrderFilterRequest.builder()
                    .page(0).size(10).sortBy("createdAt").sortDir("asc").build();
 
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
 
            PageResponse<OrderResponse> result = orderService.getOrdersFiltered(filter);
 
            assertThat(result.getContent()).isEmpty();
            verifyNoInteractions(userServiceClient);
        }
    }


    
    @Nested
    @DisplayName("getOrdersByUserId")
    class GetOrdersByUserId {
 
        @Test
        @DisplayName("calls getUserByEmail once and shares result across all orders")
        void shouldReturnOrdersForUser() {
            when(orderRepository.findByUserId(5L)).thenReturn(List.of(order));
            when(userServiceClient.getUserByEmail(USER_EMAIL)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(orderResponse);
 
            List<OrderResponse> result = orderService.getOrdersByUserId(5L);
 
            assertThat(result).hasSize(1);
            verify(userServiceClient, times(1)).getUserByEmail(USER_EMAIL);
        }
 
        @Test
        @DisplayName("returns empty list without calling user service")
        void shouldReturnEmptyListWithoutCallingUserService() {
            when(orderRepository.findByUserId(5L)).thenReturn(List.of());
 
            assertThat(orderService.getOrdersByUserId(5L)).isEmpty();
            verifyNoInteractions(userServiceClient);
        }
    }
    @Nested
    @DisplayName("updateOrder")
    class UpdateOrder {
 
        @Test
        @DisplayName("updates order and enriches response via getUserByEmail")
        void shouldUpdateOrder() {
            UpdateOrderRequest req = UpdateOrderRequest.builder()
                    .status(OrderStatus.CONFIRMED).totalPrice(2999L)
                    .orderItems(List.of(new OrderItemRequest(1L, 3))).build();
 
            OrderResponse updated = OrderResponse.builder().id(10L)
                    .status(OrderStatus.CONFIRMED).user(user).build();
 
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            doNothing().when(orderMapper).updateEntityFromRequest(req, order);
            when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(orderRepository.save(order)).thenReturn(order);
            when(userServiceClient.getUserByEmail(USER_EMAIL)).thenReturn(user);
            when(orderMapper.toResponse(order, user)).thenReturn(updated);
 
            OrderResponse result = orderService.updateOrder(10L, req);
 
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(userServiceClient).getUserByEmail(USER_EMAIL);
        }
 
        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());
            UpdateOrderRequest request = UpdateOrderRequest.builder()
                .status(OrderStatus.CANCELLED)
                .totalPrice(0L)
                .build();

            Throwable thrown = catchThrowable(() -> orderService.updateOrder(999L, request));

            assertThat(thrown).isInstanceOf(OrderNotFoundException.class);
                    
 
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(userServiceClient);
        }
    }

    @Nested
    @DisplayName("deleteOrder")
    class DeleteOrder {

        @Test
        @DisplayName("soft-deletes existing order without calling user service")
        void shouldSoftDeleteOrder() {
            when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
            doNothing().when(orderRepository).delete(order);
 
            assertThatCode(() -> orderService.deleteOrder(10L)).doesNotThrowAnyException();
 
            verify(orderRepository).delete(order);
            verifyNoInteractions(userServiceClient);
        }
 
        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());
 
            assertThatThrownBy(() -> orderService.deleteOrder(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}