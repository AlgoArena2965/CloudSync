package com.cloudsync.dto.response;

import com.cloudsync.model.enums.Role;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private Role role;
    private Boolean isEnabled;
    private Boolean isEmailVerified;
    private Long storageUsedBytes;
    private Long organizationId;
    private String organizationName;
    private LocalDateTime createdAt;
}
