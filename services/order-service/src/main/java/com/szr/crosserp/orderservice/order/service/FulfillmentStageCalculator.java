package com.szr.crosserp.orderservice.order.service;

import com.szr.crosserp.orderservice.order.model.ExternalOrderStatus;
import com.szr.crosserp.orderservice.order.model.FulfillmentStage;
import com.szr.crosserp.orderservice.order.model.ProcurementRequestStatus;
import com.szr.crosserp.orderservice.order.model.SalesOrder;

import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentStageCalculator {

    public FulfillmentStage calculate(SalesOrder order, Collection<ProcurementRequestStatus> requestStatuses) {
        return calculate(order, requestStatuses, false, null);
    }

    public FulfillmentStage calculate(
        SalesOrder order, Collection<ProcurementRequestStatus> requestStatuses,
        boolean fullyCovered, String packingStatus
    ) {
        if (order.getExternalStatus() == ExternalOrderStatus.CANCELED) {
            return FulfillmentStage.CANCELED;
        }
        if (order.isFulfillmentException()) {
            return FulfillmentStage.EXCEPTION;
        }
        if (order.getExternalStatus() == ExternalOrderStatus.DELIVERED) {
            return FulfillmentStage.COMPLETED;
        }
        if ("COMPLETED".equals(packingStatus)) {
            return FulfillmentStage.PACKED;
        }
        if ("DRAFT".equals(packingStatus)) {
            return FulfillmentStage.PACKING;
        }
        if (fullyCovered) {
            return FulfillmentStage.READY_FOR_PACKING;
        }
        if (requestStatuses.contains(ProcurementRequestStatus.CONVERTED)) {
            return FulfillmentStage.PROCUREMENT_IN_PROGRESS;
        }
        if (requestStatuses.contains(ProcurementRequestStatus.REQUESTED)) {
            return FulfillmentStage.WAITING_PROCUREMENT;
        }
        return FulfillmentStage.PENDING;
    }
}
