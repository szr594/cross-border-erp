package com.szr.crosserp.integration.procurement;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.events.enabled:false} && ${app.procurement.projection-enabled:true}")
public class ProcurementEventBinding {
    private final ProcurementSnapshotConsumer consumer;
    public ProcurementEventBinding(ProcurementSnapshotConsumer consumer){this.consumer=consumer;}
    @Bean Declarables procurementTopology(){
        var exchange=new TopicExchange("cross-erp.events",true,false);
        var dead=new DirectExchange("cross-erp.dead",true,false);
        var queue=QueueBuilder.durable("legacy.procurement-sync").deadLetterExchange("cross-erp.dead").deadLetterRoutingKey("legacy.procurement-sync").build();
        var dlq=new Queue("legacy.procurement-sync.dead",true);
        return new Declarables(exchange,dead,queue,dlq,BindingBuilder.bind(queue).to(exchange).with("procurement.#"),BindingBuilder.bind(dlq).to(dead).with("legacy.procurement-sync"));
    }
    @RabbitListener(queues="legacy.procurement-sync")
    public void receive(Message message)throws java.io.IOException{consumer.accept(message.getBody());}
}
