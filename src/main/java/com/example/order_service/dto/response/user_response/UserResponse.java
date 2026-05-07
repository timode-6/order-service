package com.example.order_service.dto.response.user_response;
 
import java.io.Serializable;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse implements Serializable {
    private Long id;
    private String name;
    private String surname;
    private String email;

    private LocalDate birthDate; 
    
    private boolean active;
}