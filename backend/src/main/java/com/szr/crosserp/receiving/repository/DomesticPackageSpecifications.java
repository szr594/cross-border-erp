package com.szr.crosserp.receiving.repository;

import com.szr.crosserp.receiving.dto.DomesticPackageQuery;
import com.szr.crosserp.receiving.model.DomesticPackage;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class DomesticPackageSpecifications {
    private DomesticPackageSpecifications() {
    }

    public static Specification<DomesticPackage> from(DomesticPackageQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.keyword() != null && !query.keyword().isBlank()) {
                String keyword = "%" + query.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("trackingNumber")), keyword),
                    builder.like(builder.lower(root.get("packageNo")), keyword),
                    builder.like(builder.lower(root.get("purchaseOrder").get("purchaseOrderNo")), keyword)
                ));
            }
            if (query.supplierId() != null) {
                predicates.add(builder.equal(root.get("purchaseOrder").get("supplier").get("id"), query.supplierId()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
