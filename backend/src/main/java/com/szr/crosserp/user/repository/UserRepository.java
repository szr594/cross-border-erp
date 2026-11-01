package com.szr.crosserp.user.repository;

import com.szr.crosserp.user.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginNameIgnoreCase(String loginName);
}

