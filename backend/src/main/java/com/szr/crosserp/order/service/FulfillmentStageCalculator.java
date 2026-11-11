package com.szr.crosserp.order.service;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.FulfillmentStage;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.packing.model.PackingTaskStatus;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentStageCalculator {

    public FulfillmentStage calculate(SalesOrder order, Collection<ProcurementRequestStatus> requestStatuses) {
        return calculate(order, requestStatuses, false, null);
    }

    public FulfillmentStage calculate(
        SalesOrder order, Collection<ProcurementRequestStatus> requestStatuses,
        boolean fullyCovered, PackingTaskStatus packingStatus
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
        if (packingStatus == PackingTaskStatus.COMPLETED) {
            return FulfillmentStage.PACKED;
        }
        if (packingStatus == PackingTaskStatus.DRAFT) {
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
