package com.szr.crosserp.orderimport.repository;

import com.szr.crosserp.orderimport.model.OrderImportError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderImportErrorRepository extends JpaRepository<OrderImportError, Long> {
    Page<OrderImportError> findByBatchId(String batchId, Pageable pageable);
}
