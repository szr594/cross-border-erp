package com.szr.crosserp.procurementservice;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
class DemandSnapshotContractTest extends ReceivingFactsContractTest {
 @Autowired com.szr.crosserp.procurementservice.events.DemandSnapshotConsumer demands;
 byte[] snapshot(long version,String status)throws Exception{
  var order=Map.of("orderId",11,"orderVersion",version,"orderNo","DEMAND-11","identityKey","1:DEMAND-11","externalStatus","NEW","exception",false,"items",List.of(Map.of("orderItemId",12,"productName","Test","quantity",2,"fulfillmentStatus","NORMAL")));
  return mapper.writeValueAsBytes(Map.of("eventId",UUID.randomUUID().toString(),"eventType","order.procurement.snapshot.v1","schemaVersion",1,"aggregateId",13,"aggregateVersion",version,"payload",Map.of("requestId",13,"orderItemId",12,"quantity",2,"status",status,"orderSnapshot",order)));
 }
 @Test @Transactional void duplicatesAndOlderSnapshotsCannotReopenConvertedDemand()throws Exception{
  byte[] first=snapshot(0,"REQUESTED");demands.accept(first);demands.accept(first);
  demands.accept(snapshot(1,"CONVERTED"));demands.accept(snapshot(0,"REQUESTED"));
  assertThat(jdbc.queryForObject("select status from procurement_request_projections where request_id=13",String.class)).isEqualTo("CONVERTED");
  assertThat(jdbc.queryForObject("select count(*) from procurement_item_projections where order_item_id=12",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from procurement_event_inbox where consumer_name='order-demands'",Integer.class)).isEqualTo(3);
 }
 @Test @Transactional void conflictingSameVersionIsRejected()throws Exception{
  demands.accept(snapshot(1,"REQUESTED"));
  assertThatThrownBy(()->demands.accept(snapshot(1,"CONVERTED"))).isInstanceOf(IllegalArgumentException.class);
 }
}
