package com.szr.crosserp.procurementservice.events;
import org.springframework.context.annotation.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.events.enabled",havingValue="true")
public class ReceivingEventBinding {
 private final ReceivingFactsConsumer consumer;
 public ReceivingEventBinding(ReceivingFactsConsumer consumer){this.consumer=consumer;}
 @Bean Declarables receivingTopology(){
  var exchange=new TopicExchange("cross-erp.events",true,false);var dead=new DirectExchange("cross-erp.dead",true,false);
  var queue=QueueBuilder.durable("procurement.receiving-sync").deadLetterExchange("cross-erp.dead").deadLetterRoutingKey("procurement.receiving-sync").build();
  var dlq=new Queue("procurement.receiving-sync.dead",true);
  return new Declarables(exchange,dead,queue,dlq,BindingBuilder.bind(queue).to(exchange).with("receiving.#"),BindingBuilder.bind(dlq).to(dead).with("procurement.receiving-sync"));
 }
 @RabbitListener(queues="procurement.receiving-sync") public void receive(Message message)throws java.io.IOException{consumer.accept(message.getBody());}
}
