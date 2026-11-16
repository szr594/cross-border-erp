package com.szr.crosserp.identity.auth.repository;

import com.szr.crosserp.identity.auth.model.RefreshSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Query("select s.user.id from RefreshSession s where s.tokenHash = :hash")
    Optional<Long> findUserIdByTokenHash(@Param("hash") String hash);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :hash")
    Optional<RefreshSession> findLockedByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("update RefreshSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
