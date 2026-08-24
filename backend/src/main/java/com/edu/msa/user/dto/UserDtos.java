package com.edu.msa.user.dto;

import com.edu.msa.common.Role;
import jakarta.validation.constraints.NotNull;

public final class UserDtos {
    private UserDtos() {}

    public record UserResponse(String name, String dept, Role role) {}

    public record RoleUpdateRequest(@NotNull Role role) {}
}
