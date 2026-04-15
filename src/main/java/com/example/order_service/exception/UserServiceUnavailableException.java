package com.example.order_service.exception;

public class UserServiceUnavailableException extends UserServiceException {
 
    public UserServiceUnavailableException(String message) {
        super(message);
    }
 
    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
 