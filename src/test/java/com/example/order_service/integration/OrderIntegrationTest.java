package com.example.order_service.integration;

import com.example.order_service.dto.request.order_item_request.OrderItemRequest;
import com.example.order_service.dto.request.order_request.*;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.*;
import com.example.order_service.dto.response.ErrorResponse;
import com.example.order_service.model.Item;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.ItemRepository;
import com.example.order_service.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderIntegrationTest extends BaseIntegrationTest {

    @Autowired 
    private ItemRepository itemRepository;
    @Autowired
    private OrderRepository orderRepository;

    private Item savedItem;

    private static final String USER_JSON = """
            {
              "id": 5,
              "name": "Alice",
              "surname": "Rossi",
              "email": "alice.rossi@example.com",
              "birthDate": "2024-12-03T10:15:30.00Z"
            }
            """;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();

        Item item = new Item();
        item.setName("Widget");
        item.setPrice(999L);

        savedItem = itemRepository.save(item);
    }


    private void stubUserById(Long userId) {
        wireMock.stubFor(get(urlPathEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(USER_JSON)));
    }

    private void stubUserServiceDown(Long userId) {
        wireMock.stubFor(get(urlPathEqualTo("/api/users/" + userId))
                .willReturn(aResponse().withStatus(503)));
    }

    private CreateOrderRequest buildCreateRequest() {
        return CreateOrderRequest.builder()
                .userId(5L)
                .totalPrice(1998L)
                .orderItems(List.of(new OrderItemRequest(savedItem.getId(), 2)))
                .build();
    }

    private ResponseEntity<OrderResponse> postOrder(CreateOrderRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl("/api/orders"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                OrderResponse.class);
    }


    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        @Test
        @DisplayName("returns 400 when request body is invalid")
        void shouldReturn400OnInvalidRequest() {
            CreateOrderRequest invalid = CreateOrderRequest.builder()
                    .userId(null)   
                    .totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(savedItem.getId(), 1)))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    baseUrl("/api/orders"),
                    HttpMethod.POST,
                    new HttpEntity<>(invalid, headers),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getFieldErrors()).containsKey("userId");
        }

        @Test
        @DisplayName("returns 404 when item does not exist")
        void shouldReturn404WhenItemMissing() {
            stubUserById(5L);
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .userId(5L)
                    .totalPrice(10L)
                    .orderItems(List.of(new OrderItemRequest(99999L, 1)))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    baseUrl("/api/orders"),
                    HttpMethod.POST,
                    new HttpEntity<>(req, headers),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("still creates order when User Service is down (circuit breaker fallback)")
        void shouldCreateOrderWithNullUserOnCircuitBreaker() {
            stubUserServiceDown(5L);

            ResponseEntity<OrderResponse> response = postOrder(buildCreateRequest());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("returns order with user info when found")
        void shouldReturnOrderById() {
            stubUserById(5L);
            OrderResponse created = postOrder(buildCreateRequest()).getBody();

            ResponseEntity<OrderResponse> response = restTemplate.getForEntity(
                    baseUrl("/api/orders/" + created.getId()),
                    OrderResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getId()).isEqualTo(created.getId());
        }

        @Test
        @DisplayName("returns 404 for non-existent order")
        void shouldReturn404() {
            ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(
                    baseUrl("/api/orders/999999"),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getMessage()).contains("999999");
        }
    }


    @Nested
    @DisplayName("GET /api/orders")
    class GetOrdersPaginated {

        @Test
        @DisplayName("returns paginated orders with status filter")
        void shouldReturnFilteredPage() {
            stubUserById(5L);
            postOrder(buildCreateRequest());
            postOrder(buildCreateRequest());

            ResponseEntity<PageResponse<OrderResponse>> response = restTemplate.exchange(
                    baseUrl("/api/orders?statuses=PENDING&page=0&size=10"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).isNotEmpty();
            assertThat(response.getBody().getContent())
                    .allMatch(o -> o.getStatus() == OrderStatus.PENDING);
        }

        @Test
        @DisplayName("returns empty page when no orders match date range")
        void shouldReturnEmptyPageForFutureDate() {
            stubUserById(5L);
            postOrder(buildCreateRequest());

            ResponseEntity<PageResponse<OrderResponse>> response = restTemplate.exchange(
                    baseUrl("/api/orders?createdFrom=2099-01-01T00:00:00Z"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).isEmpty();
        }
    }


    @Nested
    @DisplayName("GET /api/orders/user/{userId}")
    class GetOrdersByUser {

        @Test
        @DisplayName("returns all orders for user")
        void shouldReturnOrdersForUser() {
            stubUserById(5L);
            postOrder(buildCreateRequest());
            postOrder(buildCreateRequest());

            ResponseEntity<OrderResponse[]> response = restTemplate.getForEntity(
                    baseUrl("/api/orders/user/5"),
                    OrderResponse[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
        }
    }


    @Nested
    @DisplayName("PUT /api/orders/{id}")
    class UpdateOrder {

        @Test
        @DisplayName("updates order status and total price")
        void shouldUpdateOrder() {
            stubUserById(5L);
            OrderResponse created = postOrder(buildCreateRequest()).getBody();

            UpdateOrderRequest updateReq = UpdateOrderRequest.builder()
                    .status(OrderStatus.CONFIRMED)
                    .totalPrice(2999L)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    baseUrl("/api/orders/" + created.getId()),
                    HttpMethod.PUT,
                    new HttpEntity<>(updateReq, headers),
                    OrderResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(response.getBody().getTotalPrice())
                    .isEqualByComparingTo(2999L);
        }

        @Test
        @DisplayName("returns 404 when updating non-existent order")
        void shouldReturn404WhenNotFound() {
            UpdateOrderRequest req = UpdateOrderRequest.builder()
                    .status(OrderStatus.CANCELLED)
                    .totalPrice(0L)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    baseUrl("/api/orders/999999"),
                    HttpMethod.PUT,
                    new HttpEntity<>(req, headers),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }


    @Nested
    @DisplayName("DELETE /api/orders/{id}")
    class DeleteOrder {

        @Test
        @DisplayName("soft-deletes order and returns 204")
        void shouldSoftDeleteOrder() {
            stubUserById(5L);
            OrderResponse created = postOrder(buildCreateRequest()).getBody();

            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    baseUrl("/api/orders/" + created.getId()),
                    HttpMethod.DELETE,
                    null,
                    Void.class);

            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            ResponseEntity<ErrorResponse> getResponse = restTemplate.getForEntity(
                    baseUrl("/api/orders/" + created.getId()),
                    ErrorResponse.class);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 404 when deleting non-existent order")
        void shouldReturn404WhenNotFound() {
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    baseUrl("/api/orders/999999"),
                    HttpMethod.DELETE,
                    null,
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}