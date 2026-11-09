package com.szr.crosserp.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mysql-integration")
class MySqlInventorySchemaIT {
    private static final Set<String> INVENTORY_TABLES = Set.of(
        "INVENTORY_LOTS",
        "INVENTORY_ALLOCATIONS",
        "INVENTORY_MOVEMENTS"
    );

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;

    @Test
    void mysqlAppliesVersionFiveAndExposesInventoryTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet result = connection.getMetaData().getTables(
                 connection.getCatalog(), null, "%", new String[] {"TABLE"}
             )) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toUpperCase());
            }
        }
        assertThat(tables).containsAll(INVENTORY_TABLES);
    }
}
