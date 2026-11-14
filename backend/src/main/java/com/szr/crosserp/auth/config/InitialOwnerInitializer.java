package com.szr.crosserp.auth.config;

import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import com.szr.crosserp.user.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("init-owner")
public class InitialOwnerInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final String login;
    private final String password;
    public InitialOwnerInitializer(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
        @Value("${INITIAL_OWNER_LOGIN:owner}") String login,
        @Value("${INITIAL_OWNER_PASSWORD:}") String password) {
        this.users=users; this.roles=roles; this.encoder=encoder; this.login=login; this.password=password;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() != 0) throw new IllegalStateException("Initialization requires an empty user table");
        if (login == null || !login.matches("[a-zA-Z0-9_-]{3,100}") || password == null || password.length() < 12)
            throw new IllegalStateException("Set INITIAL_OWNER_LOGIN and INITIAL_OWNER_PASSWORD (minimum 12 characters)");
        User user = new User(login, "管理员", encoder.encode(password), false);
        user.addRole(roles.findByCode("OWNER").orElseThrow());
        users.saveAndFlush(user);
    }
}
