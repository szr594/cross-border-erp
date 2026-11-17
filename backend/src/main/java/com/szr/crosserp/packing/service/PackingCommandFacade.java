package com.szr.crosserp.packing.service;
import com.szr.crosserp.packing.dto.PackingDtos.*;
import com.szr.crosserp.integration.orders.OrderExecutionGate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.function.Supplier;
@Service
public class PackingCommandFacade {
    private final PackingCommandService service;private final OrderExecutionGate gate;private final JdbcTemplate jdbc;
    public PackingCommandFacade(PackingCommandService service,OrderExecutionGate gate,JdbcTemplate jdbc){this.service=service;this.gate=gate;this.jdbc=jdbc;}
    public CommandResponse start(Long orderId,StartRequest input,String actor,List<String> roles){OrderExecutionGate.requireRole(roles,java.util.Set.of("OWNER","PACKING"),"PACKING_FORBIDDEN");return gate.execute(List.of(orderId),()->service.start(orderId,input,actor,roles));}
    public CommandResponse addBox(Long id,SaveBoxRequest input,String actor,List<String> roles){return task(id,roles,()->service.addBox(id,input,actor,roles));}
    public CommandResponse updateBox(Long id,Long box,SaveBoxRequest input,String actor,List<String> roles){return task(id,roles,()->service.updateBox(id,box,input,actor,roles));}
    public CommandResponse deleteBox(Long id,Long box,Long version,String actor,List<String> roles){return task(id,roles,()->service.deleteBox(id,box,version,actor,roles));}
    public CommandResponse complete(Long id,CompleteRequest input,String actor,List<String> roles){
        if(jdbc.queryForObject("select count(*) from packing_tasks where id=? and status='COMPLETED' and completion_key=?",Long.class,id,input.idempotencyKey())>0)return service.complete(id,input,actor,roles);
        return task(id,roles,()->service.complete(id,input,actor,roles));
    }
    public CommandResponse cancel(Long id,CancelRequest input,String actor,List<String> roles){return task(id,roles,()->service.cancel(id,input,actor,roles));}
    private CommandResponse task(Long id,List<String> roles,Supplier<CommandResponse> action){OrderExecutionGate.requireRole(roles,java.util.Set.of("OWNER","PACKING"),"PACKING_FORBIDDEN");return gate.execute(jdbc.queryForList("select sales_order_id from packing_tasks where id=?",Long.class,id),action);}
}
