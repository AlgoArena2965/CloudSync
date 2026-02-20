package com.cloudsync.security.filter;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.Principal;

@Getter
@AllArgsConstructor
public class AuthenticatedUser implements Principal {

    private final Long userId;
    private final String email;
    private final String role;
    private final Long organizationId;
    private final String password;

    @Override
    public String getName() {
        return email;
    }

    public boolean hasOrganization() {
        return organizationId != null;
    }

    public boolean isAdmin() {
        return "ROLE_SUPER_ADMIN".equals(role) || "ROLE_ORG_ADMIN".equals(role);
    }
}
