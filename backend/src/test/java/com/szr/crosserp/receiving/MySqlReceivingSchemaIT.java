package com.szr.crosserp.receiving;

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
class MySqlReceivingSchemaIT {
    private static final Set<String> RECEIVING_TABLES = Set.of(
        "DOMESTIC_PACKAGES",
        "GOODS_RECEIPTS",
        "GOODS_RECEIPT_ITEMS",
        "UNEXPECTED_RECEIPT_ITEMS",
        "RECEIVING_EVENTS"
    );

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;

    @Test
    void mysqlAppliesVersionFourAndExposesReceivingTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet result = connection.getMetaData().getTables(
                 connection.getCatalog(), null, "%", new String[] {"TABLE"}
             )) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toUpperCase());
            }
        }
        assertThat(tables).containsAll(RECEIVING_TABLES);
    }
}
