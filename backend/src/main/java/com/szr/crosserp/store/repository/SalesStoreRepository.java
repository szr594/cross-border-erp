package com.szr.crosserp.store.repository;

import com.szr.crosserp.store.model.SalesStore;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface SalesStoreRepository extends JpaRepository<SalesStore, Long> {
    Optional<SalesStore> findByCode(String code);
    Optional<SalesStore> findByChannelIdAndExternalStoreId(Long channelId, String externalStoreId);

    @EntityGraph(attributePaths = "channel")
    List<SalesStore> findAllByOrderByNameAsc();
}
