package com.szr.crosserp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.szr.crosserp.order.model.ExternalOrderStatus;
import com.szr.crosserp.order.model.FulfillmentStage;
import com.szr.crosserp.order.model.ProcurementRequestStatus;
import com.szr.crosserp.order.model.SalesOrder;
import com.szr.crosserp.order.service.FulfillmentStageCalculator;
import java.util.List;
import org.junit.jupiter.api.Test;

class FulfillmentStageCalculatorTest {

    private final FulfillmentStageCalculator calculator = new FulfillmentStageCalculator();

    @Test
    void appliesApprovedPriorityOrder() {
        SalesOrder order = order(ExternalOrderStatus.CANCELED, true);
        assertThat(calculator.calculate(order, List.of(ProcurementRequestStatus.CONVERTED)))
            .isEqualTo(FulfillmentStage.CANCELED);

        order = order(ExternalOrderStatus.PROCESSING, true);
        assertThat(calculator.calculate(order, List.of(ProcurementRequestStatus.CONVERTED)))
            .isEqualTo(FulfillmentStage.EXCEPTION);

        order = order(ExternalOrderStatus.DELIVERED, false);
        assertThat(calculator.calculate(order, List.of(ProcurementRequestStatus.CONVERTED)))
            .isEqualTo(FulfillmentStage.COMPLETED);
    }

    @Test
    void usesAvailableProcurementFactsAndOtherwiseReturnsPending() {
        SalesOrder order = order(ExternalOrderStatus.PROCESSING, false);
        assertThat(calculator.calculate(order, List.of(
            ProcurementRequestStatus.REQUESTED,
            ProcurementRequestStatus.CONVERTED
        ))).isEqualTo(FulfillmentStage.PROCUREMENT_IN_PROGRESS);
        assertThat(calculator.calculate(order, List.of(ProcurementRequestStatus.REQUESTED)))
            .isEqualTo(FulfillmentStage.WAITING_PROCUREMENT);
        assertThat(calculator.calculate(order, List.of()))
            .isEqualTo(FulfillmentStage.PENDING);
    }

    private SalesOrder order(ExternalOrderStatus status, boolean exception) {
        SalesOrder order = mock(SalesOrder.class);
        when(order.getExternalStatus()).thenReturn(status);
        when(order.isFulfillmentException()).thenReturn(exception);
        return order;
    }
}
