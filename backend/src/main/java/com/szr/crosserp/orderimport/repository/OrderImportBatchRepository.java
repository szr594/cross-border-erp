package com.szr.crosserp.orderimport.repository;

import com.szr.crosserp.orderimport.model.OrderImportBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderImportBatchRepository extends JpaRepository<OrderImportBatch, String> {
    @Override
    @EntityGraph(attributePaths = "createdBy")
    Optional<OrderImportBatch> findById(String id);
}
