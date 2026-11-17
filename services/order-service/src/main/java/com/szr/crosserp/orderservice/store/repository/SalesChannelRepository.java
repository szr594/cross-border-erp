package com.szr.crosserp.orderservice.store.repository;

import com.szr.crosserp.orderservice.store.model.SalesChannel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesChannelRepository extends JpaRepository<SalesChannel, Long> {
    Optional<SalesChannel> findByCode(String code);
}
