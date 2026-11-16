package com.szr.crosserp.identity.user.repository;

import com.szr.crosserp.identity.user.model.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}
