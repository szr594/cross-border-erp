package com.szr.crosserp.orderservice.order.repository;

import com.szr.crosserp.orderservice.order.dto.OrderQuery;
import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;
import com.szr.crosserp.orderservice.order.model.FulfillmentStage;
import com.szr.crosserp.orderservice.order.model.OrderProcurementRequest;
import com.szr.crosserp.orderservice.order.model.OrderItemFulfillmentStatus;
import com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus;
import com.szr.crosserp.orderservice.order.model.SalesOrder;
import com.szr.crosserp.orderservice.order.model.SalesOrderItem;
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
        return from(request, new com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient.Snapshot(java.util.Map.of(), java.util.Map.of(), java.util.List.of()));
    }
    public static Specification<SalesOrder> from(OrderQuery request,
        com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient.Snapshot snapshot) {
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
            predicates.add(stage(request.stage(), snapshot).toPredicate(root, query, builder));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<SalesOrder> stage(String value) {
        return stage(value, new com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient.Snapshot(java.util.Map.of(), java.util.Map.of(), java.util.List.of()));
    }
    public static Specification<SalesOrder> stage(String value,
        com.szr.crosserp.orderservice.order.service.FulfillmentFactsClient.Snapshot snapshot) {
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
            var packedIds = snapshot.packing().entrySet().stream().filter(e -> "COMPLETED".equals(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            var packingIds = snapshot.packing().entrySet().stream().filter(e -> "DRAFT".equals(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            Predicate packed = packedIds.isEmpty() ? builder.disjunction() : root.get("id").in(packedIds);
            Predicate packing = packingIds.isEmpty() ? builder.disjunction() : root.get("id").in(packingIds);
            Predicate ready = snapshot.readyOrderIds().isEmpty() ? builder.disjunction() : root.get("id").in(snapshot.readyOrderIds());
            Predicate needsProcurement = builder.and(normalFlow, builder.not(packed), builder.not(packing), builder.not(ready));
            return switch (stage) {
                case CANCELED -> builder.equal(root.get("externalStatus"), ExternalOrderStatus.CANCELED);
                case EXCEPTION -> builder.and(notCanceled, builder.isTrue(root.get("fulfillmentException")));
                case COMPLETED -> builder.and(notCanceled, noException,
                    builder.equal(root.get("externalStatus"), ExternalOrderStatus.DELIVERED));
                case PACKED -> builder.and(normalFlow, packed);
                case PACKING -> builder.and(normalFlow, builder.not(packed), packing);
                case READY_FOR_PACKING -> builder.and(normalFlow, builder.not(packed), builder.not(packing), ready);
                case PROCUREMENT_IN_PROGRESS -> builder.and(needsProcurement, converted);
                case WAITING_PROCUREMENT -> builder.and(needsProcurement, builder.not(converted), requested);
                case PENDING -> builder.and(needsProcurement, builder.not(converted), builder.not(requested));
                case PARTIALLY_ALLOCATED, DOMESTIC_TRANSIT, PARTIALLY_RECEIVED, SHIPPED -> builder.disjunction();
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
