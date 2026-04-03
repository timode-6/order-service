package com.example.order_service.service.client;

import com.example.order_service.dto.response.user_response.UserResponse;
import com.example.order_service.exception.UserServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.user-service.base-url}")
    private String userServiceBaseUrl;

    private static final String CB_NAME = "userService";

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getUserByEmailFallback")
    public UserResponse getUserByEmail(String email) {
        String url = userServiceBaseUrl + "?email={email}";
        log.debug("Calling User Service: GET {}", url);
        try {
            ResponseEntity<UserResponse> response =
                    restTemplate.getForEntity(url, UserResponse.class, email);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("User with email '{}' not found in User Service", email);
            return null;
        } catch (RestClientException ex) {
            log.error("User Service call failed: {}", ex.getMessage());
            throw new UserServiceException("User Service is currently unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public UserResponse getUserByEmailFallback(String email, Throwable ex) {
        log.warn("Circuit breaker fallback triggered for email '{}': {}", email, ex.getMessage());
        return null;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getUserByIdFallback")
    public UserResponse getUserById(Long userId) {
        String url = userServiceBaseUrl + "/{id}";
        log.debug("Calling User Service: GET {}", url);
        try {
            ResponseEntity<UserResponse> response =
                    restTemplate.getForEntity(url, UserResponse.class, userId);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("User with id '{}' not found in User Service", userId);
            return null;
        } catch (RestClientException ex) {
            log.error("User Service call failed: {}", ex.getMessage());
            throw new UserServiceException("User Service is currently unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public UserResponse getUserByIdFallback(Long userId, Throwable ex) {
        log.warn("Circuit breaker fallback triggered for userId '{}': {}", userId, ex.getMessage());
        return null;
    }
}