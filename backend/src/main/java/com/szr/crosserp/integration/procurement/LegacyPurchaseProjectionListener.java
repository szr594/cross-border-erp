package com.szr.crosserp.integration.procurement;
import com.szr.crosserp.procurement.model.*;
import jakarta.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class LegacyPurchaseProjectionListener {
 private final JdbcTemplate jdbc;
 public LegacyPurchaseProjectionListener(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @PostPersist @PostUpdate public void project(Object value){
  if(value instanceof PurchaseOrder order){
   var supplier=order.getSupplier();
   jdbc.update("insert ignore into purchase_supplier_projections(id,code,name) values(?,?,?)",supplier.getId(),supplier.getCode(),supplier.getName());
   jdbc.update("insert ignore into purchase_business_projections(id,purchase_order_no,supplier_id,status,currency,bridge_confirmed,source_version,version) values(?,?,?,?,?,?,?,?)",order.getId(),order.getPurchaseOrderNo(),supplier.getId(),order.getStatus().name(),order.getCurrency(),order.isBridgeConfirmed(),order.getVersion(),order.getVersion());
   requireLegacy(order.getId());
   jdbc.update("update purchase_business_projections set status=?,bridge_confirmed=?,source_version=?,version=version+1 where id=?",order.getStatus().name(),order.isBridgeConfirmed(),order.getVersion(),order.getId());
  }else if(value instanceof PurchaseOrderItem item){
   requireLegacy(item.getPurchaseOrder().getId());
   jdbc.update("insert ignore into purchase_item_projections select * from purchase_order_items where id=?",item.getId());
   jdbc.update("update purchase_item_projections set received_quantity=?,version=version+1 where id=?",item.getReceivedQuantity(),item.getId());
  }else if(value instanceof PurchaseOrderAllocation allocation){
   requireLegacy(allocation.getPurchaseOrderItem().getPurchaseOrder().getId());
   jdbc.update("insert ignore into purchase_allocation_projections select * from purchase_order_allocations where id=?",allocation.getId());
  }
 }
 public void refreshLegacy(long id){
  jdbc.update("insert ignore into purchase_supplier_projections(id,code,name) select s.id,s.code,s.name from suppliers s join purchase_orders o on o.supplier_id=s.id where o.id=?",id);
  jdbc.update("insert ignore into purchase_business_projections(id,purchase_order_no,supplier_id,status,currency,bridge_confirmed,source_version,authority) select id,purchase_order_no,supplier_id,status,currency,bridge_confirmed,version,'LEGACY' from purchase_orders where id=?",id);
  jdbc.update("insert ignore into purchase_item_projections select * from purchase_order_items where purchase_order_id=?",id);
  jdbc.update("insert ignore into purchase_allocation_projections select a.* from purchase_order_allocations a join purchase_order_items i on i.id=a.purchase_order_item_id where i.purchase_order_id=?",id);

  jdbc.update("update purchase_business_projections p set status=(select o.status from purchase_orders o where o.id=p.id),bridge_confirmed=(select o.bridge_confirmed from purchase_orders o where o.id=p.id) where p.id=? and p.authority='LEGACY'",id);
 }
 public void mirrorReceiving(long id){
  boolean legacy=jdbc.queryForObject("select count(*) from purchase_business_projections where id=? and authority='LEGACY'",Integer.class,id)>0;
  if(!legacy)return;

  jdbc.update("update purchase_orders o set status=(select p.status from purchase_business_projections p where p.id=o.id) where o.id=? and exists(select 1 from purchase_business_projections p where p.id=o.id and p.authority='LEGACY')",id);
  jdbc.update("update purchase_order_items i set received_quantity=(select p.received_quantity from purchase_item_projections p where p.id=i.id) where i.purchase_order_id=? and exists(select 1 from purchase_business_projections p where p.id=i.purchase_order_id and p.authority='LEGACY')",id);

 }
 private void requireLegacy(long id){
  String owner=jdbc.queryForObject("select authority from purchase_business_projections where id=? for update",String.class,id);
  if(!"LEGACY".equals(owner))throw new IllegalStateException("Procurement service owns this purchase");
 }
}
