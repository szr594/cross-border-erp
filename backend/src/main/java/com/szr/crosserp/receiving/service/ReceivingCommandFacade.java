package com.szr.crosserp.receiving.service;
import com.szr.crosserp.receiving.dto.*;
import com.szr.crosserp.integration.orders.OrderExecutionGate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
@Service
public class ReceivingCommandFacade {
    private final ReceivingCommandService service;private final OrderExecutionGate gate;private final JdbcTemplate jdbc;
    public ReceivingCommandFacade(ReceivingCommandService service,OrderExecutionGate gate,JdbcTemplate jdbc){this.service=service;this.gate=gate;this.jdbc=jdbc;}
    public DomesticPackageCommandResponse register(CreateDomesticPackageRequest input,String actor,List<String> roles){return service.register(input,actor,roles);}
    public DomesticPackageCommandResponse cancel(Long id,CancelDomesticPackageRequest input,String actor,List<String> roles){return service.cancel(id,input,actor,roles);}
    public ConfirmGoodsReceiptResponse confirm(Long packageId,ConfirmGoodsReceiptRequest input,String actor,List<String> roles){
        OrderExecutionGate.requireRole(roles,java.util.Set.of("OWNER","WAREHOUSE"),"RECEIVING_FORBIDDEN");
        if(input.idempotencyKey()!=null&&jdbc.queryForObject("select count(*) from goods_receipts where idempotency_key=?",Long.class,input.idempotencyKey().trim())>0)return service.confirm(packageId,input,actor,roles);
        var ids=jdbc.queryForList("select distinct i.order_id from domestic_packages p join purchase_order_items pi on pi.purchase_order_id=p.purchase_order_id join purchase_order_allocations a on a.purchase_order_item_id=pi.id join procurement_request_projections r on r.request_id=a.procurement_request_id join order_item_business_projections i on i.order_item_id=r.order_item_id where p.id=?",Long.class,packageId);
        return gate.execute(ids,()->service.confirm(packageId,input,actor,roles));
    }
}
