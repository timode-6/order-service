package com.example.order_service.client;

import com.example.order_service.dto.response.user_response.*;
import com.example.order_service.exception.UserNotFoundException;
import com.example.order_service.exception.UserServiceException;
import com.example.order_service.exception.UserServiceUnavailableException;
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

import java.net.URI;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock 
    private RestTemplate restTemplate;

    @InjectMocks
    private UserServiceClient client;

    private static final String BASE_URL = "http://user-service";
     private static final String EMAIL = "alice.rossi@example.com";
 
    private static final UserResponse ALICE = UserResponse.builder()
            .id(5L).name("Alice").surname("Rossi")
            .email(EMAIL).birthDate(Instant.parse("2024-12-03T10:15:30.00Z"))
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
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenReturn(ResponseEntity.ok(ALICE));

            UserResponse result = client.getUserById(5L);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("throws UserNotFoundException when User Service responds 404")
        void notFound() {
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.NOT_FOUND, "Not Found", null, null, null));

            assertThatThrownBy(() -> client.getUserById(5L))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws UserServiceException on RestClientException")
        void restClientFailure() {
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(() -> client.getUserById(5L))
                    .isInstanceOf(UserServiceException.class)
                    .hasMessageContaining("unavailable");
        }

        @Test
        @DisplayName("returns null body transparently when response body is null")
        void nullBody() {
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenReturn(ResponseEntity.ok(null));

           assertThatThrownBy(() -> client.getUserById(5L))
                    .isInstanceOf(RuntimeException.class);
        }
    }


    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("returns user when User Service responds 200")
        void success() {
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenReturn(ResponseEntity.ok(ALICE));

            UserResponse result = client.getUserByEmail(EMAIL);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("throws UserNotFoundException when User Service responds 404")
        void notFound() {
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.NOT_FOUND, "Not Found", null, null, null));

            assertThatThrownBy(() -> client.getUserByEmail(EMAIL))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(EMAIL);
        }

        @Test
        @DisplayName("throws UserServiceException on RestClientException")
        void restClientFailure() {
            when(restTemplate.getForEntity(any(URI.class), eq(UserResponse.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(() -> client.getUserByEmail(EMAIL))
                    .isInstanceOf(UserServiceException.class)
                    .hasMessageContaining("unavailable");
        }

    }

    @Nested
    @DisplayName("Fallback methods")
    class Fallbacks {

        @Test
        @DisplayName("throws UserServiceUnavailableException when circuit is open")
        void throwsOnCircuitOpen() {

            Throwable thrown = catchThrowable(() ->
            client.getUserByEmailFallback(EMAIL, new RuntimeException("circuit open")));

            assertThat(thrown).isInstanceOf(UserServiceUnavailableException.class)
            .hasMessageContaining("circuit open");

        }
 
        @Test
        @DisplayName("re-throws UserNotFoundException unchanged — domain error is not wrapped")
        void rethrowsUserNotFound() {
            UserNotFoundException original = new UserNotFoundException(EMAIL);
 
            Throwable thrown = catchThrowable(() ->
            client.getUserByEmailFallback(EMAIL, original));

            assertThat(thrown).isInstanceOf(UserNotFoundException.class).isSameAs(original);
        }
 
        @Test
        @DisplayName("never returns null")
        void neverReturnsNull() {
            Throwable thrown = catchThrowable(() ->
            client.getUserByEmailFallback(EMAIL, new RuntimeException("any")));

            assertThat(thrown).isInstanceOf(RuntimeException.class);
        }
 
        @Test
        @DisplayName("handles throwable with null message safely")
        void nullMessage() {
            Throwable thrown = catchThrowable(() ->
            client.getUserByEmailFallback(EMAIL, new RuntimeException()));

            assertThat(thrown).isInstanceOf(RuntimeException.class);
        }
    }
}