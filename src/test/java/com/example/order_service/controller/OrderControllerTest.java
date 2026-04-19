package com.example.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.order_service.dto.request.order_item_request.*;
import com.example.order_service.dto.request.order_request.*;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.*;
import com.example.order_service.dto.response.user_response.*;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.exception.*;
import com.example.order_service.service.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired 
    MockMvc mvc;

    ObjectMapper om = new ObjectMapper();

    @MockitoBean 
    OrderService orderService;

    private OrderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        UserResponse user = UserResponse.builder()
                .id(5L).name("Alice").surname("Rossi")
                .email("alice.rossi@example.com").birthDate(Instant.parse("2024-12-03T10:15:30.00Z")).active(true).build();

        sampleResponse = OrderResponse.builder()
                .id(10L).userId(5L)
                .status(OrderStatus.PENDING)
                .totalPrice(1998L)
                .orderItems(List.of())
                .user(user)
                .createdAt(Instant.parse("2024-06-01T10:00:00Z"))
                .updatedAt(Instant.parse("2024-06-01T10:00:00Z"))
                .build();
    }

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        private CreateOrderRequest validRequest() {
            return CreateOrderRequest.builder()
                    .userId(5L)
                    .userEmail("alice.rossi@example.com")
                    .totalPrice(1998L)
                    .orderItems(List.of(new OrderItemRequest(1L, 2)))
                    .build();
        }

        @Test
        @DisplayName("201 and order body on valid request")
        void created() throws Exception {
            when(orderService.createOrder(any())).thenReturn(sampleResponse);

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.user.email").value("alice.rossi@example.com"));
        }

        @Test
        @DisplayName("400 with fieldErrors when userId is null")
        void rejectsNullUserId() throws Exception {
            CreateOrderRequest bad = CreateOrderRequest.builder()
                    .userId(null)
                    .userEmail("alice.rossi@example.com")
                    .totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(1L, 1)))
                    .build();

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.userId").exists());
        }

        @Test
        @DisplayName("400 when totalPrice is zero (not positive)")
        void rejectsZeroPrice() throws Exception {
            CreateOrderRequest bad = CreateOrderRequest.builder()
                    .userId(1L)
                    .userEmail("alice.rossi@example.com")
                    .totalPrice(0L)
                    .orderItems(List.of(new OrderItemRequest(1L, 1)))
                    .build();

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.totalPrice").exists());
        }

        @Test
        @DisplayName("400 when orderItems list is empty")
        void rejectsEmptyItems() throws Exception {
            CreateOrderRequest bad = CreateOrderRequest.builder()
                    .userId(1L)
                    .userEmail("alice.rossi@example.com")
                    .totalPrice(0L)
                    .orderItems(List.of())
                    .build();

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.orderItems").exists());
        }

        @Test
        @DisplayName("400 when nested orderItem has quantity < 1")
        void rejectsZeroQuantity() throws Exception {
            CreateOrderRequest bad = CreateOrderRequest.builder()
                    .userId(1L)
                    .userEmail("alice.rossi@example.com")
                    .totalPrice(0L)
                    .orderItems(List.of(new OrderItemRequest(1L, 0)))
                    .build();

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors['orderItems[0].quantity']").exists());
        }

        @Test
        @DisplayName("404 when item not found (ItemNotFoundException)")
        void itemNotFound() throws Exception {
            when(orderService.createOrder(any()))
                    .thenThrow(new ItemNotFoundException(99L));

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("99")));
                    
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetById {

        @Test
        @DisplayName("200 and order body when found")
        void found() throws Exception {
            when(orderService.getOrderById(10L)).thenReturn(sampleResponse);

            mvc.perform(get("/api/orders/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.userId").value(5));
        }

        @Test
        @DisplayName("404 when order does not exist")
        void notFound() throws Exception {
            when(orderService.getOrderById(999L))
                    .thenThrow(new OrderNotFoundException(999L));

            mvc.perform(get("/api/orders/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }

        @Test
        @DisplayName("400 when id is not a number (type mismatch)")
        void typeMismatch() throws Exception {
            mvc.perform(get("/api/orders/not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }
    }

    @Nested
    @DisplayName("GET /api/orders")
    class GetOrders {

        @Test
        @DisplayName("200 with default pagination")
        void defaultPagination() throws Exception {
            PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                    .content(List.of(sampleResponse))
                    .page(0).size(20).totalElements(1).totalPages(1).last(true)
                    .build();
            when(orderService.getOrdersFiltered(any())).thenReturn(page);

            mvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.last").value(true));
        }

        @Test
        @DisplayName("200 with status and date-range filters")
        void withFilters() throws Exception {
            PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                    .content(List.of(sampleResponse))
                    .page(0).size(10).totalElements(1).totalPages(1).last(true)
                    .build();
            when(orderService.getOrdersFiltered(any())).thenReturn(page);

            mvc.perform(get("/api/orders")
                            .param("statuses", "PENDING")
                            .param("createdFrom", "2024-01-01T00:00:00Z")
                            .param("createdTo", "2024-12-31T23:59:59Z")
                            .param("page", "0")
                            .param("size", "10")
                            .param("sortBy", "createdAt")
                            .param("sortDir", "asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("200 with multiple statuses")
        void multipleStatuses() throws Exception {
            PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                    .content(List.of()).page(0).size(20)
                    .totalElements(0).totalPages(0).last(true).build();
            when(orderService.getOrdersFiltered(any())).thenReturn(page);

            mvc.perform(get("/api/orders")
                            .param("statuses", "PENDING", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/orders/user/{userId}")
    class GetByUser {

        @Test
        @DisplayName("200 with orders list")
        void returnsOrders() throws Exception {
            when(orderService.getOrdersByUserId(5L)).thenReturn(List.of(sampleResponse));

            mvc.perform(get("/api/orders/user/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(5));
        }

        @Test
        @DisplayName("200 with empty list when user has no orders")
        void emptyList() throws Exception {
            when(orderService.getOrdersByUserId(99L)).thenReturn(List.of());

            mvc.perform(get("/api/orders/user/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{id}")
    class UpdateOrder {

        private UpdateOrderRequest validUpdate() {
            return UpdateOrderRequest.builder()
                    .status(OrderStatus.CONFIRMED)
                    .totalPrice(2999L)
                    .build();
        }

        @Test
        @DisplayName("200 with updated order body")
        void updated() throws Exception {
            OrderResponse updated = OrderResponse.builder()
                    .id(10L).status(OrderStatus.CONFIRMED)
                    .totalPrice(2999L)
                    .userId(5L).user(sampleResponse.getUser()).build();

            when(orderService.updateOrder(eq(10L), any())).thenReturn(updated);

            mvc.perform(put("/api/orders/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validUpdate())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalPrice").value(2999L));
        }

        @Test
        @DisplayName("404 when order not found")
        void notFound() throws Exception {
            when(orderService.updateOrder(eq(999L), any()))
                    .thenThrow(new OrderNotFoundException(999L));

            mvc.perform(put("/api/orders/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validUpdate())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("400 when status is null")
        void rejectsNullStatus() throws Exception {
            UpdateOrderRequest bad = UpdateOrderRequest.builder()
                    .status(null)
                    .totalPrice(10L)
                    .build();

            mvc.perform(put("/api/orders/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status").exists());
        }

        @Test
        @DisplayName("400 when totalPrice is null")
        void rejectsNullPrice() throws Exception {
            UpdateOrderRequest bad = UpdateOrderRequest.builder()
                    .status(OrderStatus.CONFIRMED)
                    .totalPrice(null)
                    .build();

            mvc.perform(put("/api/orders/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.totalPrice").exists());
        }
    }

    @Nested
    @DisplayName("DELETE /api/orders/{id}")
    class DeleteOrder {

        @Test
        @DisplayName("204 on successful soft-delete")
        void deleted() throws Exception {
            doNothing().when(orderService).deleteOrder(10L);

            mvc.perform(delete("/api/orders/10"))
                    .andExpect(status().isNoContent());

            verify(orderService).deleteOrder(10L);
        }

        @Test
        @DisplayName("404 when order not found")
        void notFound() throws Exception {
            doThrow(new OrderNotFoundException(999L))
                    .when(orderService).deleteOrder(999L);

            mvc.perform(delete("/api/orders/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }
    }


    @Nested
    @DisplayName("GlobalExceptionHandler")
    class ExceptionHandling {

        @Test
        @DisplayName("503 on UserServiceException")
        void userServiceUnavailable() throws Exception {
            when(orderService.getOrderById(1L))
                    .thenThrow(new UserServiceException("User Service is currently unavailable"));

            mvc.perform(get("/api/orders/1"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value(containsString("unavailable")));
        }

        @Test
        @DisplayName("400 on IllegalArgumentException")
        void illegalArgument() throws Exception {
            when(orderService.getOrderById(1L))
                    .thenThrow(new IllegalArgumentException("bad argument"));

            mvc.perform(get("/api/orders/1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("bad argument"));
        }

        @Test
        @DisplayName("500 on unexpected exception")
        void genericException() throws Exception {
            when(orderService.getOrderById(1L))
                    .thenThrow(new RuntimeException("something exploded"));

            mvc.perform(get("/api/orders/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }

    }
}