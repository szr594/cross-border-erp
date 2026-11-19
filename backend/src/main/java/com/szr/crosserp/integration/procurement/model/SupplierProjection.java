package com.szr.crosserp.integration.procurement.model;
import jakarta.persistence.*;
@Entity @Table(name="purchase_supplier_projections")
public class SupplierProjection {
 @Id private Long id;
 private String code;
 private String name;
 protected SupplierProjection() {}
 public Long getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public static SupplierProjection fromLegacy(com.szr.crosserp.procurement.model.Supplier s){var p=new SupplierProjection();p.id=s.getId();p.code=s.getCode();p.name=s.getName();return p;}
}
