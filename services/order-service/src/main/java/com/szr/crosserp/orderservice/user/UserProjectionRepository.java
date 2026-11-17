package com.szr.crosserp.orderservice.user;

import com.szr.crosserp.orderservice.user.model.UserProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectionRepository extends JpaRepository<UserProjection,Long> {
    java.util.Optional<UserProjection> findByLoginNameIgnoreCase(String loginName);
}
