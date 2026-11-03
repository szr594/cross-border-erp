package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.model.ReceivingEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReceivingEventRepository extends JpaRepository<ReceivingEvent, Long> {
    List<ReceivingEvent> findByDomesticPackageIdOrderByOccurredAtDescIdDesc(Long domesticPackageId);
    Page<ReceivingEvent> findByDomesticPackageId(Long domesticPackageId, Pageable pageable);
}
