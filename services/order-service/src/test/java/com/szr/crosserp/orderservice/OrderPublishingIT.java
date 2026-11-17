package com.szr.crosserp.orderservice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import com.szr.crosserp.orderservice.events.OrderOutboxPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:mysql://127.0.0.1:13318/cross_erp_order_it2?connectionTimeZone=UTC",
    "spring.datasource.username=order_app","spring.datasource.password=order-it-app-only","app.events.enabled=true",
    "app.events.publish-delay-ms=600000","spring.rabbitmq.port=15673","spring.rabbitmq.username=events_it","spring.rabbitmq.password=events-it-only"})
class OrderPublishingIT {
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) throws Exception {OrderFoundationTest.keys(registry);}
    @Autowired OrderOutboxPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Test void realBrokerConfirmsOutboxPublication() throws Exception {
        String id=java.util.UUID.randomUUID().toString();long aggregate=Math.abs(java.util.UUID.randomUUID().getMostSignificantBits()%1000000000)+1;
        String payload="{\"eventId\":\""+id+"\",\"eventType\":\"order.created.v1\",\"schemaVersion\":1,\"aggregateId\":"+aggregate+",\"aggregateVersion\":0,\"payload\":{\"orderId\":"+aggregate+",\"items\":[{\"orderItemId\":1,\"quantity\":2}]}}";
        jdbc.update("insert into outbox_events(event_id,event_type,aggregate_id,aggregate_version,payload,occurred_at) values(?,'order.created.v1',?,0,?,CURRENT_TIMESTAMP(6))",id,aggregate,payload);
        publisher.publish();
        assertThat(jdbc.queryForObject("select published_at from outbox_events where event_id=?",java.sql.Timestamp.class,id)).isNotNull();
        var message=rabbit.receive("legacy.order-sync",10000);
        assertThat(message).isNotNull();assertThat(message.getMessageProperties().getMessageId()).isEqualTo(id);
        assertThat(new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(payload);
    }
}
