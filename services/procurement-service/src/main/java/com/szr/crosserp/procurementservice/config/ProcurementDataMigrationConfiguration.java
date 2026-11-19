package com.szr.crosserp.procurementservice.config;

import com.szr.crosserp.procurementservice.migration.ProcurementDataMigrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@ConditionalOnProperty(name="app.procurement.migration.enabled",havingValue="true")
public class ProcurementDataMigrationConfiguration {
    @Bean ApplicationRunner migrateProcurementData(ProcurementDataMigrationService service,ConfigurableApplicationContext context,
        com.szr.crosserp.procurementservice.migration.ProcurementRollbackService rollback,
        @Value("${app.procurement.migration.direction:forward}") String direction,
        @Value("${app.procurement.remote-enabled:false}") boolean remoteEnabled,
        @Value("${PROCUREMENT_MIGRATION_SOURCE_URL}") String url,
        @Value("${PROCUREMENT_MIGRATION_SOURCE_USER}") String user,
        @Value("${PROCUREMENT_MIGRATION_SOURCE_PASSWORD}") String password,
        @Value("${app.events.enabled:false}") boolean eventsEnabled) {
        return arguments -> {
            if (eventsEnabled || remoteEnabled) throw new IllegalStateException("Disable the publisher during maintenance migration");
            var legacy=new DriverManagerDataSource(url,user,password);
            java.util.Map<String,Long> counts;
            if("forward".equals(direction))counts=service.migrate(legacy);
            else if("reverse".equals(direction))counts=rollback.restore(legacy);
            else throw new IllegalArgumentException("Unknown migration direction");
            counts.forEach((table,count)->System.out.println("Migrated "+table+": "+count));
            context.close();
        };
    }
}
