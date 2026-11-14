package com.szr.crosserp.auth;

import com.szr.crosserp.auth.config.InitialOwnerInitializer;
import com.szr.crosserp.user.repository.UserRepository;
import com.szr.crosserp.user.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class InitialOwnerInitializerTest {
    @Test
    void existingUsersRejectInitializationWithoutChangingPasswords() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.count()).thenReturn(1L);
        var initializer = new InitialOwnerInitializer(users, roles, encoder, "owner", "test-only-password");
        assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty user table");
        verifyNoInteractions(roles, encoder);
        verify(users, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
