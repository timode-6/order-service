package com.example.order_service.exception;


public class UserServiceException extends RuntimeException {
    public UserServiceException(String message) {
        super(message);
    }
 
    public UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}