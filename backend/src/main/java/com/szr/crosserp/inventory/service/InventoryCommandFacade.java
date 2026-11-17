package com.szr.crosserp.inventory.service;
import com.szr.crosserp.inventory.dto.*;
import com.szr.crosserp.integration.orders.OrderExecutionGate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
@Service
public class InventoryCommandFacade {
    private final InventoryCommandService service;private final OrderExecutionGate gate;private final JdbcTemplate jdbc;
    public InventoryCommandFacade(InventoryCommandService service,OrderExecutionGate gate,JdbcTemplate jdbc){this.service=service;this.gate=gate;this.jdbc=jdbc;}
    public InventoryCommandResponse allocate(Long lotId,ManualInventoryAllocationRequest input,String actor,List<String> roles){
        OrderExecutionGate.requireRole(roles,java.util.Set.of("OWNER","WAREHOUSE"),"INVENTORY_FORBIDDEN");
        if(input.idempotencyKey()!=null&&jdbc.queryForObject("select count(*) from inventory_movements where idempotency_key=?",Long.class,input.idempotencyKey())>0)return service.allocate(lotId,input,actor,roles);
        var ids=jdbc.queryForList("select order_id from order_item_business_projections where order_item_id=?",Long.class,input.orderItemId());
        return gate.execute(ids,()->service.allocate(lotId,input,actor,roles));
    }
    public InventoryCommandResponse release(Long id,ReleaseInventoryAllocationRequest input,String actor,List<String> roles){return service.release(id,input,actor,roles);}
    public InventoryCommandResponse adjust(Long id,AdjustInventoryRequest input,String actor,List<String> roles){return service.adjust(id,input,actor,roles);}
    public InventoryCommandResponse releaseQuarantine(Long id,ReleaseQuarantineRequest input,String actor,List<String> roles){return service.releaseQuarantine(id,input,actor,roles);}
}
