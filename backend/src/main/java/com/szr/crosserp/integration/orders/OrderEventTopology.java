package com.szr.crosserp.integration.orders;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.core.*;
@Configuration
@ConditionalOnProperty(name="app.events.enabled",havingValue="true")
public class OrderEventTopology {
    @Bean Declarables orderTopology(){
        var exchange=new TopicExchange("cross-erp.events",true,false);
        var dead=new DirectExchange("cross-erp.dead",true,false);
        var queue=QueueBuilder.durable("legacy.order-sync").deadLetterExchange("cross-erp.dead").deadLetterRoutingKey("legacy.order-sync").build();
        var dlq=new Queue("legacy.order-sync.dead",true);
        return new Declarables(exchange,dead,queue,dlq,
            BindingBuilder.bind(queue).to(exchange).with("order.#"),
            BindingBuilder.bind(dlq).to(dead).with("legacy.order-sync"));
    }
}
