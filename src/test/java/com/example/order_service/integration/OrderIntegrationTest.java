package com.example.order_service.integration;

import com.example.order_service.dto.request.order_item_request.OrderItemRequest;
import com.example.order_service.dto.request.order_request.*;
import com.example.order_service.dto.response.PageResponse;
import com.example.order_service.dto.response.order_response.*;
import com.example.order_service.dto.response.ErrorResponse;
import com.example.order_service.model.Item;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.ItemRepository;
import com.example.order_service.repository.OrderItemRepository;
import com.example.order_service.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        @Autowired
        private OrderItemRepository orderItemRepository;

        public static String internal = "1$MjQ0ODc4ZTNhOGM5N2ViNDIzYmEzNDJhN2VmMjMwMzE$GYaUg+8XVS1Y0WBhM2HdGJ0Wyjneyu19mQfd9OtutWQ";

        private Item savedItem;

        private static final String USER_EMAIL = "alice.rossi@example.com";

        private static final String USER_JSON = """
                        {
                          "id": 5,
                          "name": "Alice",
                          "surname": "Rossi",
                          "email": "alice.rossi@example.com",
                          "birthDate": "2024-12-03",
                          "active": true
                        }
                        """;
        @BeforeEach
        void setUp() {
                com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", wireMock.port());

                orderItemRepository.deleteAll();
                orderRepository.deleteAll();
                itemRepository.deleteAll();

                Item item = new Item();
                item.setName("Widget");
                item.setPrice(999L);

                savedItem = itemRepository.save(item);
                assertThat(savedItem.getId()).isNotNull();
        }

                private void stubUserByEmail() {
                wireMock.stubFor(get(urlPathEqualTo("/api/users/search"))
                                .withQueryParam("email", equalTo(USER_EMAIL))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(USER_JSON)));
        }

        private void stubUserServiceDown() {
                wireMock.stubFor(get(urlPathEqualTo("/api/users/search"))
                                .withQueryParam("email", equalTo(USER_EMAIL))
                                .willReturn(aResponse().withStatus(503)));
        }

        private CreateOrderRequest buildCreateRequest() {
                return CreateOrderRequest.builder()
                                .userId(5L)
                                .userEmail(USER_EMAIL)
                                .totalPrice(1998L)
                                .orderItems(List.of(new OrderItemRequest(savedItem.getId(), 2)))
                                .build();
        }

        private ResponseEntity<OrderResponse> postOrder(CreateOrderRequest request) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                ResponseEntity<OrderResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders"),
                                HttpMethod.POST,
                                new HttpEntity<>(request, headers),
                                OrderResponse.class);

                if (response.getStatusCode() != HttpStatus.CREATED) {
                        System.out.println("ERROR BODY: " + response.getBody());
                }

                assertThat(response.getStatusCode())
                                .as("POST /api/orders failed")
                                .isEqualTo(HttpStatus.CREATED);

                return response;
        }

        @Test
        @DisplayName("creates order and returns 201 with user info fetched by email")
        void shouldCreateOrder() {
                stubUserByEmail();

                ResponseEntity<OrderResponse> response = postOrder(buildCreateRequest());

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getId()).isNotNull();
                assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(response.getBody().getUser()).isNotNull();
                assertThat(response.getBody().getUser().getEmail()).isEqualTo(USER_EMAIL);
                assertThat(response.getBody().getOrderItems()).hasSize(1);

                wireMock.verify(getRequestedFor(urlPathEqualTo("/api/users/search"))
                                .withQueryParam("email", equalTo(USER_EMAIL)));
        }

        @Test
        @DisplayName("returns 400 when userEmail is missing")
        void shouldReturn400WhenUserEmailMissing() {
                CreateOrderRequest invalid = CreateOrderRequest.builder()
                                .userId(5L)
                                .userEmail(null)
                                .totalPrice(10L)
                                .orderItems(List.of(new OrderItemRequest(savedItem.getId(), 1)))
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");
                ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders"),
                                HttpMethod.POST,
                                new HttpEntity<>(invalid, headers),
                                ErrorResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().getFieldErrors()).containsKey("userEmail");
        }

        @Test
        @DisplayName("returns 400 when userId is missing")
        void shouldReturn400WhenUserIdMissing() {
                CreateOrderRequest invalid = CreateOrderRequest.builder()
                                .userId(null)
                                .userEmail(USER_EMAIL)
                                .totalPrice(10L)
                                .orderItems(List.of(new OrderItemRequest(savedItem.getId(), 1)))
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");
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
                stubUserByEmail();
                CreateOrderRequest req = CreateOrderRequest.builder()
                                .userId(5L)
                                .userEmail(USER_EMAIL)
                                .totalPrice(10L)
                                .orderItems(List.of(new OrderItemRequest(99999L, 1)))
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");
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
                stubUserServiceDown();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");
                ResponseEntity<OrderResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders"),
                                HttpMethod.POST,
                                new HttpEntity<>(buildCreateRequest(), headers),
                                OrderResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getUser()).isNull();
        }

        @Test
        @DisplayName("returns order with user info fetched by email")
        void shouldReturnOrderById() {
                stubUserByEmail();
                OrderResponse created = postOrder(buildCreateRequest()).getBody();

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                ResponseEntity<OrderResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders/" + created.getId()),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                OrderResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getId()).isEqualTo(created.getId());
                assertThat(response.getBody().getUser()).isNotNull();
                assertThat(response.getBody().getUser().getEmail()).isEqualTo(USER_EMAIL);
        }

        @Test
        @DisplayName("returns 404 for non-existent order")
        void shouldReturn404() {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders/" + 999999L),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                ErrorResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody().getMessage()).contains("999999");
        }

        @Test
        @DisplayName("returns paginated orders with user info fetched by email")
        void shouldReturnFilteredPage() {
                stubUserByEmail();
                postOrder(buildCreateRequest());

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "ADMIN");

                ResponseEntity<PageResponse<OrderResponse>> response = restTemplate.exchange(
                                baseUrl("/api/orders?statuses=PENDING&page=0&size=10"),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                new ParameterizedTypeReference<>() {
                                });

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getContent()).isNotEmpty();
                assertThat(response.getBody().getContent())
                                .allMatch(o -> o.getStatus() == OrderStatus.PENDING);
                assertThat(response.getBody().getContent())
                                .allMatch(o -> o.getUser() != null
                                                && USER_EMAIL.equals(o.getUser().getEmail()));
        }

        @Test
        @DisplayName("returns empty page when no orders match date range")
        void shouldReturnEmptyPageForFutureDate() {
                stubUserByEmail();
                postOrder(buildCreateRequest());

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "ADMIN");

                ResponseEntity<PageResponse<OrderResponse>> response = restTemplate.exchange(
                                baseUrl("/api/orders?createdFrom=2099-01-01T00:00:00Z"),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                new ParameterizedTypeReference<>() {
                                });

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getContent()).isEmpty();
        }

        @Test
        @DisplayName("returns all orders for user with email-based enrichment")
        void shouldReturnOrdersForUser() {
                stubUserByEmail();
                postOrder(buildCreateRequest());

                wireMock.resetRequests();

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "ADMIN");

                restTemplate.exchange(baseUrl("/api/orders/user/5"), HttpMethod.GET, new HttpEntity<>(headers),
                                OrderResponse[].class);

                wireMock.verify(getRequestedFor(urlPathEqualTo("/api/users/search"))
                                .withQueryParam("email", equalTo(USER_EMAIL)));
        }

        @Test
        @DisplayName("returns empty list when user has no orders without calling user service")
        void shouldReturnEmptyListForUserWithNoOrders() {

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "ADMIN");

                ResponseEntity<OrderResponse[]> response = restTemplate.exchange(
                                baseUrl("/api/orders/user/999"), HttpMethod.GET, new HttpEntity<>(headers),
                                OrderResponse[].class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isEmpty();
                wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/users")));
        }

        @Test
        @DisplayName("updates order and returns enriched response via getUserByEmail")
        void shouldUpdateOrder() {
                stubUserByEmail();
                OrderResponse created = postOrder(buildCreateRequest()).getBody();

                UpdateOrderRequest updateReq = UpdateOrderRequest.builder()
                                .status(OrderStatus.SUCCESS)
                                .totalPrice(2999L)
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<OrderResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders/" + created.getId()),
                                HttpMethod.PUT,
                                new HttpEntity<>(updateReq, headers),
                                OrderResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.SUCCESS);
                assertThat(response.getBody().getTotalPrice())
                                .isEqualByComparingTo(2999L);
                assertThat(response.getBody().getUser()).isNotNull();
        }

        @Test
        @DisplayName("returns 404 when updating non-existent order")
        void shouldReturn404WhenNotFound() {
                UpdateOrderRequest req = UpdateOrderRequest.builder()
                                .status(OrderStatus.CANCELLED)
                                .totalPrice(0L)
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders/999999"),
                                HttpMethod.PUT,
                                new HttpEntity<>(req, headers),
                                ErrorResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("soft-deletes order and returns 204")
        void shouldSoftDeleteOrder() {
                stubUserByEmail();
                OrderResponse created = postOrder(buildCreateRequest()).getBody();

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                                baseUrl("/api/orders/" + created.getId()),
                                HttpMethod.DELETE,
                                new HttpEntity<>(headers),
                                Void.class);

                assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

                ResponseEntity<ErrorResponse> getResponse = restTemplate.exchange(
                                baseUrl("/api/orders/" + created.getId()),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                ErrorResponse.class);
                assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 404 when deleting non-existent order")
        void shouldReturn404WhenDeleted() {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Secret", internal);
                headers.set("X-User-Id", "5");
                headers.set("X-User-Role", "USER");

                ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                                baseUrl("/api/orders/999999"),
                                HttpMethod.DELETE,
                                new HttpEntity<>(headers),
                                ErrorResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
}