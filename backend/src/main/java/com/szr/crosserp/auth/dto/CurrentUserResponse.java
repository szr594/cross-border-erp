package com.szr.crosserp.auth.dto;

import com.szr.crosserp.user.model.User;
import java.util.List;

public record CurrentUserResponse(
    Long id,
    String name,
    String loginName,
    List<String> roles,
    List<String> permissions,
    boolean mustChangePassword
) {
    public static CurrentUserResponse from(User user) {
        List<String> roleCodes = user.getRoles().stream()
            .map(role -> role.getCode())
            .sorted()
            .toList();
        List<String> permissions = roleCodes.contains("OWNER") ? List.of("*") : List.of();
        return new CurrentUserResponse(
            user.getId(),
            user.getDisplayName(),
            user.getLoginName(),
            roleCodes,
            permissions,
            user.isMustChangePassword()
        );
    }
}

