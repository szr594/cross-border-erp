package com.szr.crosserp.integration.orders;

import com.szr.crosserp.order.model.*;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;

/** Transactional adapter for the retained monolith; never writes ORDER-owned projections. */
@Component
public class LegacyProjectionListener {
    private final JdbcTemplate jdbc;
    public LegacyProjectionListener(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @PostPersist @PostUpdate
    public void project(Object entity){
        if(entity instanceof SalesOrder o){
            jdbc.update("insert ignore into order_business_projections(order_id,order_no,external_status,fulfillment_exception,source_version,updated_at,authority) values(?,?,?,?,?,CURRENT_TIMESTAMP(6),'LEGACY')",o.getId(),o.getOrderNo(),o.getExternalStatus().name(),o.isFulfillmentException(),o.getVersion());
            requireLegacy(o.getId());
            jdbc.update("update order_business_projections set order_no=?,external_status=?,fulfillment_exception=?,responsible_user_id=?,source_version=?,identity_key=?,store_name=?,requested_ship_at=?,updated_at=CURRENT_TIMESTAMP(6) where order_id=? and authority='LEGACY'",
                o.getOrderNo(),o.getExternalStatus().name(),o.isFulfillmentException(),o.getResponsibleUser()==null?null:o.getResponsibleUser().getId(),o.getVersion(),o.getStore().getId()+":"+o.getExternalOrderId(),o.getStore().getName(),o.getRequestedShipAt()==null?null:Timestamp.from(o.getRequestedShipAt()),o.getId());
        }else if(entity instanceof SalesOrderItem i){
            project(i.getOrder());
            requireLegacy(i.getOrder().getId());
            jdbc.update("insert ignore into order_item_business_projections(order_item_id,order_id,product_name,quantity,fulfillment_status,source_version) values(?,?,?,?,?,?)",i.getId(),i.getOrder().getId(),i.getProductName(),i.getQuantity(),i.getFulfillmentStatus().name(),i.getOrder().getVersion());
            Long owner=jdbc.queryForObject("select order_id from order_item_business_projections where order_item_id=?",Long.class,i.getId());
            if(!i.getOrder().getId().equals(owner))throw new IllegalStateException("Order item projection identity conflict");
            jdbc.update("update order_item_business_projections set product_name=?,variant_name=?,external_sku=?,external_item_id=?,erp_sku_id=?,quantity=?,fulfillment_status=?,source_version=? where order_item_id=?",i.getProductName(),i.getVariantName(),i.getExternalSku(),i.getExternalItemId(),i.getErpSkuId(),i.getQuantity(),i.getFulfillmentStatus().name(),i.getOrder().getVersion(),i.getId());
        }else if(entity instanceof OrderProcurementRequest r){
            project(r.getOrderItem());
            requireLegacy(r.getOrderItem().getOrder().getId());
            jdbc.update("insert ignore into procurement_request_projections(request_id,order_item_id,requested_quantity,status,source_version,requested_at) values(?,?,?,?,?,?)",r.getId(),r.getOrderItem().getId(),r.getRequestedQuantity(),r.getStatus().name(),r.getVersion(),Timestamp.from(r.getRequestedAt()));
            Long item=jdbc.queryForObject("select order_item_id from procurement_request_projections where request_id=?",Long.class,r.getId());
            if(!r.getOrderItem().getId().equals(item))throw new IllegalStateException("Procurement request projection identity conflict");
            jdbc.update("update procurement_request_projections set requested_quantity=?,status=?,source_version=? where request_id=?",r.getRequestedQuantity(),r.getStatus().name(),r.getVersion(),r.getId());
        }
    }
    public void refreshOrder(long id){
        jdbc.update("insert ignore into order_business_projections(order_id,order_no,external_status,fulfillment_exception,responsible_user_id,source_version,updated_at,authority,identity_key,store_name,requested_ship_at) select o.id,o.order_no,o.external_status,o.fulfillment_exception,o.responsible_user_id,o.version,o.updated_at,'LEGACY',CONCAT(o.store_id,':',o.external_order_id),s.name,o.requested_ship_at from sales_orders o join sales_stores s on s.id=o.store_id where o.id=?",id);
        requireLegacy(id);
        jdbc.update("insert ignore into order_item_business_projections(order_item_id,order_id,product_name,external_sku,quantity,fulfillment_status,source_version,variant_name,external_item_id,erp_sku_id) select i.id,i.order_id,i.product_name,i.external_sku,i.quantity,i.fulfillment_status,o.version,i.variant_name,i.external_item_id,i.erp_sku_id from sales_order_items i join sales_orders o on o.id=i.order_id where o.id=?",id);
        jdbc.update("insert ignore into procurement_request_projections(request_id,order_item_id,requested_quantity,status,source_version,requested_at) select r.id,r.order_item_id,r.requested_quantity,r.status,r.version,r.requested_at from order_procurement_requests r join sales_order_items i on i.id=r.order_item_id where i.order_id=?",id);
    }
    private void requireLegacy(Long id){
        String authority=jdbc.queryForObject("select authority from order_business_projections where order_id=? for update",String.class,id);
        if(!"LEGACY".equals(authority))throw new IllegalStateException("Legacy write conflicts with order-service authority");
    }
}
