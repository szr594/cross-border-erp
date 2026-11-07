package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.dto.InventoryAllocationQuery;
import com.szr.crosserp.inventory.model.InventoryAllocation;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

public final class InventoryAllocationSpecifications {
    private InventoryAllocationSpecifications() {
    }

    public static Specification<InventoryAllocation> from(InventoryAllocationQuery query) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (query.orderNo() != null && !query.orderNo().isBlank()) {
                predicates.add(builder.like(
                    builder.lower(root.get("salesOrderItem").get("order").get("orderNo")),
                    "%" + query.orderNo().trim().toLowerCase() + "%"
                ));
            }
            if (query.lotNo() != null && !query.lotNo().isBlank()) {
                predicates.add(builder.like(
                    builder.lower(root.get("inventoryLot").get("lotNo")),
                    "%" + query.lotNo().trim().toLowerCase() + "%"
                ));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
