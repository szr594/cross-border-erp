package com.szr.crosserp.order.repository;

import com.szr.crosserp.order.dto.OrderQuery;
import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.FulfillmentStage;
import com.szr.crosserp.order.model.OrderProcurementRequest;
import com.szr.crosserp.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.model.SalesOrderItem;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<SalesOrder> from(OrderQuery request) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.keyword() != null && !request.keyword().isBlank()) {
                String keyword = "%" + request.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                Subquery<Long> matchingItem = query.subquery(Long.class);
                var item = matchingItem.from(SalesOrderItem.class);
                matchingItem.select(item.get("id"));
                matchingItem.where(
                    builder.equal(item.get("order").get("id"), root.get("id")),
                    builder.or(
                        builder.like(builder.lower(item.get("productName")), keyword),
                        builder.like(builder.lower(item.get("externalSku")), keyword)
                    )
                );
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("orderNo")), keyword),
                    builder.like(builder.lower(root.get("displayOrderNo")), keyword),
                    builder.like(builder.lower(root.get("externalOrderId")), keyword),
                    builder.exists(matchingItem)
                ));
            }
            if (request.storeId() != null) {
                predicates.add(builder.equal(root.get("store").get("id"), request.storeId()));
            }
            if (request.responsibleUserId() != null) {
                predicates.add(builder.equal(root.get("responsibleUser").get("id"), request.responsibleUserId()));
            }
            if (request.externalStatus() != null) {
                predicates.add(builder.equal(root.get("externalStatus"), request.externalStatus()));
            }
            if (request.orderedFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("orderedAt"), request.orderedFrom()));
            }
            if (request.orderedTo() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("orderedAt"), request.orderedTo()));
            }
            if (request.exceptionOnly()) {
                predicates.add(builder.isTrue(root.get("fulfillmentException")));
            }
            predicates.add(stage(request.stage()).toPredicate(root, query, builder));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<SalesOrder> stage(String value) {
        return (root, query, builder) -> {
            String normalized = value == null || value.isBlank()
                ? "ACTIONABLE"
                : value.trim().toUpperCase(Locale.ROOT);
            Predicate notCanceled = builder.notEqual(root.get("externalStatus"), ExternalOrderStatus.CANCELED);
            Predicate noException = builder.isFalse(root.get("fulfillmentException"));
            Predicate notCompleted = builder.notEqual(root.get("externalStatus"), ExternalOrderStatus.DELIVERED);
            Predicate normalFlow = builder.and(notCanceled, noException, notCompleted);
            Predicate requested = hasRequestStatus(root.<Long>get("id"), query, builder, ProcurementRequestStatus.REQUESTED);
            Predicate converted = hasRequestStatus(root.<Long>get("id"), query, builder, ProcurementRequestStatus.CONVERTED);

            if ("ACTIONABLE".equals(normalized)) {
                return builder.and(notCanceled, notCompleted);
            }
            FulfillmentStage stage;
            try {
                stage = FulfillmentStage.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return builder.disjunction();
            }
            return switch (stage) {
                case CANCELED -> builder.equal(root.get("externalStatus"), ExternalOrderStatus.CANCELED);
                case EXCEPTION -> builder.and(notCanceled, builder.isTrue(root.get("fulfillmentException")));
                case COMPLETED -> builder.and(notCanceled, noException,
                    builder.equal(root.get("externalStatus"), ExternalOrderStatus.DELIVERED));
                case PROCUREMENT_IN_PROGRESS -> builder.and(normalFlow, converted);
                case WAITING_PROCUREMENT -> builder.and(normalFlow, builder.not(converted), requested);
                case PENDING -> builder.and(normalFlow, builder.not(converted), builder.not(requested));
                default -> builder.disjunction();
            };
        };
    }

    private static Predicate hasRequestStatus(
        jakarta.persistence.criteria.Path<Long> orderId,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder builder,
        ProcurementRequestStatus status
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        var request = subquery.from(OrderProcurementRequest.class);
        subquery.select(request.get("id"));
        subquery.where(
            builder.equal(request.get("orderItem").get("order").get("id"), orderId),
            builder.equal(request.get("orderItem").get("fulfillmentStatus"), OrderItemFulfillmentStatus.NORMAL),
            builder.equal(request.get("status"), status)
        );
        return builder.exists(subquery);
    }
}
