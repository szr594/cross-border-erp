package com.szr.crosserp.procurement.repository;

import com.szr.crosserp.procurement.dto.PurchaseOrderQuery;
import com.szr.crosserp.procurement.model.PurchaseOrder;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseOrderSpecifications {
    private PurchaseOrderSpecifications() {
    }

    public static Specification<PurchaseOrder> from(PurchaseOrderQuery query) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (query.keyword() != null && !query.keyword().isBlank()) {
                String pattern = "%" + query.keyword().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("purchaseOrderNo")), pattern),
                    builder.like(builder.lower(root.get("supplier").get("name")), pattern),
                    builder.like(builder.lower(root.get("supplier").get("code")), pattern)
                ));
            }
            if (query.supplierId() != null) {
                predicates.add(builder.equal(root.get("supplier").get("id"), query.supplierId()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
