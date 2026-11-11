package com.szr.crosserp.orderimport.repository;

import com.szr.crosserp.orderimport.model.OrderImportRow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderImportRowRepository extends JpaRepository<OrderImportRow, Long> {
    List<OrderImportRow> findByBatchIdOrderByRowNumberAsc(String batchId);
}
