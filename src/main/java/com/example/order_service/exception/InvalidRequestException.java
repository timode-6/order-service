package com.example.order_service.exception;
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) { super(message); }
}