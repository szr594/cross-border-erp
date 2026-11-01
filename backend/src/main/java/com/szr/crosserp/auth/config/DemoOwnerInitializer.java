package com.szr.crosserp.auth.config;

import com.szr.crosserp.user.model.Role;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.RoleRepository;
import com.szr.crosserp.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(10)
public class DemoOwnerInitializer implements ApplicationRunner {

    private final String password;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoOwnerInitializer(
        @Value("${DEMO_OWNER_PASSWORD:}") String password,
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.password = password;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank() || userRepository.findByLoginNameIgnoreCase("owner").isPresent()) {
            return;
        }
        Role ownerRole = roleRepository.findByCode("OWNER")
            .orElseThrow(() -> new IllegalStateException("OWNER role is missing from the database"));
        User owner = new User("owner", "演示管理员", passwordEncoder.encode(password), false);
        owner.addRole(ownerRole);
        userRepository.save(owner);
    }
}
