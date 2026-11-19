package com.szr.crosserp.procurementservice.user;

import com.szr.crosserp.procurementservice.user.model.UserProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectionRepository extends JpaRepository<UserProjection,Long> {
    java.util.Optional<UserProjection> findByLoginNameIgnoreCase(String loginName);
}
