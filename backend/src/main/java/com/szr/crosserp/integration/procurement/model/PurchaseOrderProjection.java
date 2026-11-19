package com.szr.crosserp.integration.procurement.model;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import jakarta.persistence.*;
@Entity @Table(name="purchase_business_projections")
public class PurchaseOrderProjection {
 @Id private Long id;
 @Column(name="purchase_order_no") private String purchaseOrderNo;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supplier_id") private SupplierProjection supplier;
 @Enumerated(EnumType.STRING) private PurchaseOrderStatus status;
 @Column(name="bridge_confirmed") private boolean bridgeConfirmed;
 @Version private long version;
 protected PurchaseOrderProjection() {}
 public Long getId(){return id;} public String getPurchaseOrderNo(){return purchaseOrderNo;}
 public SupplierProjection getSupplier(){return supplier;} public PurchaseOrderStatus getStatus(){return status;}
 public boolean isBridgeConfirmed(){return bridgeConfirmed;} public long getVersion(){return version;}
 public void updateReceivingStatus(boolean any,boolean all){
  if(status!=PurchaseOrderStatus.ORDERED && status!=PurchaseOrderStatus.PARTIALLY_RECEIVED && status!=PurchaseOrderStatus.RECEIVED)throw new IllegalStateException("Purchase is not receivable");
  status=all?PurchaseOrderStatus.RECEIVED:any?PurchaseOrderStatus.PARTIALLY_RECEIVED:PurchaseOrderStatus.ORDERED;
 }
 public static PurchaseOrderProjection fromLegacy(com.szr.crosserp.procurement.model.PurchaseOrder s){var p=new PurchaseOrderProjection();p.id=s.getId();p.purchaseOrderNo=s.getPurchaseOrderNo();p.supplier=SupplierProjection.fromLegacy(s.getSupplier());p.status=s.getStatus();p.bridgeConfirmed=s.isBridgeConfirmed();p.version=s.getVersion();return p;}
}
