package com.szr.crosserp.integration.orders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.events.enabled",havingValue="true")
public class OrderEventListener {
    private final OrderEventConsumer consumer;
    public OrderEventListener(OrderEventConsumer consumer){this.consumer=consumer;}
    @RabbitListener(queues="legacy.order-sync")
    public void receive(Message message) throws java.io.IOException { consumer.accept(message.getBody()); }
}
