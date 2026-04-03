package com.example.order_service.client;

import com.example.order_service.dto.response.user_response.*;
import com.example.order_service.exception.UserServiceException;
import com.example.order_service.service.client.UserServiceClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private UserServiceClient client;

    private static final String BASE_URL = "http://user-service";
    private static final UserResponse ALICE = UserResponse.builder()
            .id(5L).name("Alice").surname("Rossi")
            .email("alice.rossi@example.com").birthDate(Instant.parse("2024-12-03T10:15:30.00Z"))
            .build();

    @BeforeEach
    void injectBaseUrl() {
        ReflectionTestUtils.setField(client, "userServiceBaseUrl", BASE_URL);
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("returns user when User Service responds 200")
        void success() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class), eq(5L)))
                    .thenReturn(ResponseEntity.ok(ALICE));

            UserResponse result = client.getUserById(5L);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("alice.rossi@example.com");
        }

        @Test
        @DisplayName("returns null when User Service responds 404")
        void notFound() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class), eq(99L)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.NOT_FOUND, "Not Found", null, null, null));

            UserResponse result = client.getUserById(99L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("throws UserServiceException on RestClientException")
        void restClientFailure() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class), eq(5L)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(() -> client.getUserById(5L))
                    .isInstanceOf(UserServiceException.class)
                    .hasMessageContaining("unavailable");
        }

        @Test
        @DisplayName("returns null body transparently when response body is null")
        void nullBody() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class), eq(5L)))
                    .thenReturn(ResponseEntity.ok(null));

            UserResponse result = client.getUserById(5L);

            assertThat(result).isNull();
        }
    }


    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("returns user when User Service responds 200")
        void success() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class),
                    eq("alice.rossi@example.com")))
                    .thenReturn(ResponseEntity.ok(ALICE));

            UserResponse result = client.getUserByEmail("alice.rossi@example.com");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("returns null when User Service responds 404")
        void notFound() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class),
                    eq("unknown@example.com")))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.NOT_FOUND, "Not Found", null, null, null));

            UserResponse result = client.getUserByEmail("unknown@example.com");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("throws UserServiceException on RestClientException")
        void restClientFailure() {
            when(restTemplate.getForEntity(anyString(), eq(UserResponse.class),
                    eq("alice.rossi@example.com")))
                    .thenThrow(new ResourceAccessException("timeout"));

            assertThatThrownBy(() -> client.getUserByEmail("alice.rossi@example.com"))
                    .isInstanceOf(UserServiceException.class)
                    .hasMessageContaining("unavailable");
        }
    }

    @Nested
    @DisplayName("Fallback methods")
    class Fallbacks {

        @Test
        @DisplayName("getUserByIdFallback returns null and does not throw")
        void getUserByIdFallback() {
            UserResponse result = client.getUserByIdFallback(
                    5L, new RuntimeException("circuit open"));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getUserByEmailFallback returns null and does not throw")
        void getUserByEmailFallback() {
            UserResponse result = client.getUserByEmailFallback(
                    "alice.rossi@example.com", new RuntimeException("circuit open"));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getUserByIdFallback is safe with null cause")
        void fallbackWithNullMessage() {
            UserResponse result = client.getUserByIdFallback(1L, new RuntimeException());
            assertThat(result).isNull();
        }
    }
}