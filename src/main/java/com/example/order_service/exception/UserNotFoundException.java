package com.example.order_service.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("User with email '" + email + "' not found in User Service");
    }
    public UserNotFoundException(Long userId){
        super("User with id '" + userId + "' not found in User Service");
    }
}
 