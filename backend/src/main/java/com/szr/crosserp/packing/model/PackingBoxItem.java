package com.szr.crosserp.packing.model;

import com.szr.crosserp.inventory.model.InventoryAllocation;
import com.szr.crosserp.order.model.SalesOrderItem;
import jakarta.persistence.*;

@Entity @Table(name="packing_box_items")
public class PackingBoxItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="packing_box_id",nullable=false) private PackingBox box;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sales_order_item_id",nullable=false) private SalesOrderItem orderItem;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="inventory_allocation_id",nullable=false) private InventoryAllocation allocation;
    @Column(nullable=false) private int quantity;
    protected PackingBoxItem() {}
    public PackingBoxItem(PackingBox box,InventoryAllocation allocation,int quantity){if(quantity<=0)throw new IllegalArgumentException();this.box=box;this.allocation=allocation;this.orderItem=allocation.getSalesOrderItem();this.quantity=quantity;}
    public Long getId(){return id;} public PackingBox getBox(){return box;} public SalesOrderItem getOrderItem(){return orderItem;} public InventoryAllocation getAllocation(){return allocation;} public int getQuantity(){return quantity;}
}
