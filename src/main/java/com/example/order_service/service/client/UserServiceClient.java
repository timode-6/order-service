package com.example.order_service.service.client;

import com.example.order_service.dto.response.user_response.UserResponse;
import com.example.order_service.exception.UserServiceUnavailableException;
import com.example.order_service.exception.UserNotFoundException;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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
        URI uri = UriComponentsBuilder
                .fromUriString(userServiceBaseUrl)
                .path("/api/users")
                .queryParam("email", email)      
                .build()
                .toUri();

        log.debug("Calling User Service: GET {}", uri);  
        try {
            ResponseEntity<UserResponse> response =
                    restTemplate.getForEntity(uri, UserResponse.class);  
            if (response.getBody() == null) {
                throw new UserServiceException("User Service returned empty response", null);
            }
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("User with email '{}' not found in User Service", email);
            throw new UserNotFoundException(email);
        } catch (RestClientException ex) {
            log.error("User Service call failed: {}", ex.getMessage());
            throw new UserServiceException("User Service is currently unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public UserResponse getUserByEmailFallback(String email, Throwable ex) {
        log.warn("Circuit breaker fallback triggered for email '{}': {}", email, ex.getMessage());
        if (ex instanceof UserNotFoundException notFound) {
            throw notFound;
        }
        throw new UserServiceUnavailableException(
                "User Service is currently unavailable (circuit open)", ex);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getUserByIdFallback")
    public UserResponse getUserById(Long userId) {
        URI uri = UriComponentsBuilder
                .fromUriString(userServiceBaseUrl)
                .path("/api/users")
                .queryParam("userId", userId)      
                .build()
                .toUri();

        log.debug("Calling User Service: GET {}", uri);  
        try {
            ResponseEntity<UserResponse> response =
                    restTemplate.getForEntity(uri, UserResponse.class);  
            if (response.getBody() == null) {
                throw new UserServiceException("User Service returned empty response", null);
            }
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
             log.warn("User with id '{}' not found in User Service", userId);
            throw new UserNotFoundException(userId);
        } catch (RestClientException ex) {
            log.error("User Service call failed: {}", ex.getMessage());
            throw new UserServiceException("User Service is currently unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public UserResponse getUserByIdFallback(Long userId, Throwable ex) {
        log.warn("Circuit breaker fallback triggered for userId '{}': {}", userId, ex.getMessage());
        if (ex instanceof UserNotFoundException notFound) { throw notFound;}
        throw new UserServiceUnavailableException(
                "User Service is currently unavailable (circuit open)", ex);
    }
}