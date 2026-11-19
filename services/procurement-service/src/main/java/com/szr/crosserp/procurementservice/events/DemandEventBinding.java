package com.szr.crosserp.procurementservice.events;
import org.springframework.context.annotation.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.events.enabled",havingValue="true")
public class DemandEventBinding {
 private final DemandSnapshotConsumer consumer;
 public DemandEventBinding(DemandSnapshotConsumer consumer){this.consumer=consumer;}
 @Bean Declarables demandTopology(){
  var exchange=new TopicExchange("cross-erp.events",true,false);var dead=new DirectExchange("cross-erp.dead",true,false);
  var queue=QueueBuilder.durable("procurement.order-sync").deadLetterExchange("cross-erp.dead").deadLetterRoutingKey("procurement.order-sync").build();
  var dlq=new Queue("procurement.order-sync.dead",true);
  return new Declarables(exchange,dead,queue,dlq,BindingBuilder.bind(queue).to(exchange).with("order.#"),BindingBuilder.bind(dlq).to(dead).with("procurement.order-sync"));
 }
 @RabbitListener(queues="procurement.order-sync") public void receive(Message message)throws java.io.IOException{consumer.accept(message.getBody());}
}
