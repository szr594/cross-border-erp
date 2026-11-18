package com.szr.crosserp.orderservice.config;

import com.szr.crosserp.orderservice.order.service.OrderDataMigrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@ConditionalOnProperty(name="app.orders.migration.enabled",havingValue="true")
public class OrderDataMigrationConfiguration {
    @Bean ApplicationRunner migrateOrderData(OrderDataMigrationService service,ConfigurableApplicationContext context,
        @Value("${ORDER_MIGRATION_SOURCE_URL}") String url,
        @Value("${ORDER_MIGRATION_SOURCE_USER}") String user,
        @Value("${ORDER_MIGRATION_SOURCE_PASSWORD}") String password,
        @Value("${app.events.enabled:false}") boolean eventsEnabled) {
        return arguments -> {
            if (eventsEnabled) throw new IllegalStateException("Disable the publisher during maintenance migration");
            var counts=service.migrate(new DriverManagerDataSource(url,user,password));
            counts.forEach((table,count)->System.out.println("Migrated "+table+": "+count));
            context.close();
        };
    }
}
