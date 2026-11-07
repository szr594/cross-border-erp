package com.szr.crosserp.inventory.repository;

import com.szr.crosserp.inventory.dto.InventoryLotQuery;
import com.szr.crosserp.inventory.model.InventoryLot;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

public final class InventoryLotSpecifications {
    private InventoryLotSpecifications() {
    }

    public static Specification<InventoryLot> from(InventoryLotQuery query) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new ArrayList<Predicate>();
            var purchaseItem = root.join("purchaseOrderItem");
            var purchaseOrder = purchaseItem.join("purchaseOrder");
            if (query.keyword() != null && !query.keyword().isBlank()) {
                String pattern = "%" + query.keyword().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("lotNo")), pattern),
                    builder.like(builder.lower(purchaseItem.get("productName")), pattern),
                    builder.like(builder.lower(purchaseItem.get("supplierSku")), pattern),
                    builder.like(builder.lower(purchaseOrder.get("purchaseOrderNo")), pattern)
                ));
            }
            if (query.purchaseOrderId() != null) {
                predicates.add(builder.equal(purchaseOrder.get("id"), query.purchaseOrderId()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.qualityStatus() != null) {
                predicates.add(builder.equal(root.get("qualityStatus"), query.qualityStatus()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
