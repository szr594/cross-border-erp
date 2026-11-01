package com.szr.crosserp.order.model;

public enum FulfillmentStage {
    PENDING("待处理"),
    PARTIALLY_ALLOCATED("部分满足"),
    WAITING_PROCUREMENT("待采购"),
    PROCUREMENT_IN_PROGRESS("采购中"),
    DOMESTIC_TRANSIT("国内运输中"),
    PARTIALLY_RECEIVED("部分到货"),
    READY_FOR_PACKING("已齐货"),
    PACKING("打包中"),
    PACKED("已打包"),
    SHIPPED("已出库"),
    COMPLETED("已完成"),
    EXCEPTION("异常"),
    CANCELED("已取消");

    private final String displayName;

    FulfillmentStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
