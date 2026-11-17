package com.szr.crosserp.orderservice.orderimport.repository;

import com.szr.crosserp.orderservice.orderimport.model.OrderImportBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderImportBatchRepository extends JpaRepository<OrderImportBatch, String> {
    @Override
    @EntityGraph(attributePaths = "createdBy")
    Optional<OrderImportBatch> findById(String id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from OrderImportBatch b where b.id = :id")
    Optional<OrderImportBatch> findLockedById(@org.springframework.data.repository.query.Param("id") String id);
}
