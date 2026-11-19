package com.szr.crosserp.integration.procurement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.events.enabled",havingValue="true")
public class ReceivingOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    public ReceivingOutboxPublisher(JdbcTemplate jdbc,RabbitTemplate rabbit){this.jdbc=jdbc;this.rabbit=rabbit;}
    @Bean TopicExchange orderExchange(){return new TopicExchange("cross-erp.events",true,false);}
    @Bean Queue legacyOrderQueue(){return QueueBuilder.durable("procurement.receiving-sync").deadLetterExchange("cross-erp.dead").deadLetterRoutingKey("procurement.receiving-sync").build();}
    @Bean Binding legacyOrderBinding(){return BindingBuilder.bind(legacyOrderQueue()).to(orderExchange()).with("receiving.#");}
    @Scheduled(fixedDelayString="${app.events.publish-delay-ms:1000}")
    @Transactional
    public void publish() {
        var rows=jdbc.queryForList("select event_id,event_type,payload from receiving_outbox where published_at is null order by occurred_at limit 50 for update skip locked");
        for(var row:rows) {
            String id=(String)row.get("event_id");
            rabbit.invoke(operations->{
                var properties=new MessageProperties();properties.setMessageId(id);
                properties.setContentType("application/json");properties.setContentEncoding("UTF-8");
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                operations.send("cross-erp.events",(String)row.get("event_type"),
                    new Message(((String)row.get("payload")).getBytes(StandardCharsets.UTF_8),properties));
                operations.waitForConfirmsOrDie(5000);return null;
            });
            jdbc.update("update receiving_outbox set published_at=CURRENT_TIMESTAMP(6),attempt_count=attempt_count+1 where event_id=?",id);
        }
    }
}
